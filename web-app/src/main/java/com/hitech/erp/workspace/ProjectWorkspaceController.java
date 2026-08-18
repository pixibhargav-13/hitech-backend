package com.hitech.erp.workspace;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.payroll.dto.PayrollDtos.ProjectManpower;
import com.hitech.erp.payroll.dto.PayrollDtos.ProjectStaffRow;
import com.hitech.erp.payroll.service.ProjectLabourService;
import com.hitech.erp.project.db.ProjectEntity;
import com.hitech.erp.project.db.ProjectRepository;
import com.hitech.erp.project.service.AccessService;
import com.hitech.erp.project.service.ProjectMemberService;
import com.hitech.erp.task.dto.TaskDtos.ProjectWorkload;
import com.hitech.erp.task.security.CurrentUser;
import com.hitech.erp.task.service.TaskService;
import com.hitech.erp.tender.dto.TenderDtos.TenderResponse;
import com.hitech.erp.tender.service.TenderService;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import com.hitech.erp.vyapar.dto.VyaparDtos.ProjectFinance;
import com.hitech.erp.vyapar.dto.VyaparDtos.ProjectMaterialRow;
import com.hitech.erp.vyapar.service.VyaparService;
import com.hitech.erp.workspace.ProjectWorkspaceDtos.ProjectProgress;
import com.hitech.erp.workspace.ProjectWorkspaceDtos.ProjectSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cross-module reads for the Project workspace.
 *
 * <p>Sibling services don't depend on each other by design — Vyapar knows nothing about tasks,
 * Payroll knows nothing about tenders. This controller lives in {@code web-app}, the one module
 * that depends on all of them, and does nothing but ask each owner for its own project rollup and
 * staple the answers together. No business logic belongs here; if a figure needs a rule, the rule
 * goes in the module that owns the data.
 *
 * <p>Each section is permission-gated independently, so a supervisor without Vyapar access still
 * gets a usable page instead of a 403.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectWorkspaceController {

  /** Manpower defaults to the last 30 days when the caller doesn't ask for a window. */
  private static final int DEFAULT_WINDOW_DAYS = 30;

  /** Reported vs derived progress this far apart is worth flagging in the UI. */
  private static final int PROGRESS_DIVERGENCE_POINTS = 20;

  private final ProjectRepository projectRepository;
  private final ProjectMemberService memberService;
  private final AccessService accessService;
  private final VyaparService vyaparService;
  private final TaskService taskService;
  private final ProjectLabourService labourService;
  private final TenderService tenderService;

  /**
   * Money for every project at once, keyed by project id — the projects <em>list</em>.
   *
   * <p>The list needs an amount per row, and asking {@code /{id}/summary} per row would be one
   * round trip per project. This is a single pass over the books, the same cost the Vyapar
   * dashboard already pays. Projects with no documents simply don't appear in the map.
   */
  @GetMapping("/finance")
  @PreAuthorize("hasAuthority('PROJECT:VIEW') and hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<Map<Long, ProjectFinance>> financeRollup() {
    return ResponseEntity.ok(vyaparService.financeByProject());
  }

  @GetMapping("/{projectId}/summary")
  @PreAuthorize("hasAuthority('PROJECT:VIEW')")
  public ResponseEntity<ProjectSummary> summary(
      @PathVariable("projectId") Long projectId,
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to) {

    ProjectEntity project = requireAccessibleProject(projectId);
    LocalDate toDate = parseOr(to, LocalDate.now());
    LocalDate fromDate = parseOr(from, toDate.minusDays(DEFAULT_WINDOW_DAYS));

    ProjectFinance finance = can("VYAPAR:VIEW") ? vyaparService.projectFinance(projectId) : null;
    ProjectWorkload tasks = can("TASKOPAD:VIEW") ? taskService.projectWorkload(projectId) : null;
    ProjectManpower manpower =
        can("PAYROLL:VIEW")
            ? labourService.manpower(projectId, memberService.getMemberIds(projectId), fromDate, toDate)
            : null;
    List<TenderResponse> tenders = can("TENDER:VIEW") ? tenderService.byProject(projectId) : null;

    int reported = project.getProgress();
    int derived = tasks == null ? 0 : tasks.completionPercent();
    ProjectProgress progress =
        new ProjectProgress(
            reported,
            derived,
            tasks != null && Math.abs(reported - derived) >= PROGRESS_DIVERGENCE_POINTS);

    return ResponseEntity.ok(
        new ProjectSummary(
            projectId, finance, tasks, manpower, tenders, progress,
            fromDate.toString(), toDate.toString()));
  }

  /**
   * The project's people: everyone assigned to it, plus anyone who punched on it. Pay figures are
   * included only for callers who can see payroll — the roster itself is visible to anyone who can
   * open the project.
   */
  @GetMapping("/{projectId}/staff")
  @PreAuthorize("hasAuthority('PROJECT:VIEW')")
  public ResponseEntity<List<ProjectStaffRow>> staff(
      @PathVariable("projectId") Long projectId,
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to) {

    requireAccessibleProject(projectId);
    LocalDate toDate = parseOr(to, LocalDate.now());
    LocalDate fromDate = parseOr(from, toDate.minusDays(DEFAULT_WINDOW_DAYS));

    return ResponseEntity.ok(
        labourService.staff(
            projectId, memberService.getMemberIds(projectId), fromDate, toDate, can("PAYROLL:VIEW")));
  }

  /** Material movement on this project — the lines of every document filed against it. */
  @GetMapping("/{projectId}/materials")
  @PreAuthorize("hasAuthority('PROJECT:VIEW') and hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<List<ProjectMaterialRow>> materials(@PathVariable("projectId") Long projectId) {
    requireAccessibleProject(projectId);
    return ResponseEntity.ok(vyaparService.projectMaterials(projectId));
  }

  /** The tender(s) this project was handed off from. */
  @GetMapping("/{projectId}/tenders")
  @PreAuthorize("hasAuthority('PROJECT:VIEW') and hasAuthority('TENDER:VIEW')")
  public ResponseEntity<List<TenderResponse>> tenders(@PathVariable("projectId") Long projectId) {
    requireAccessibleProject(projectId);
    return ResponseEntity.ok(tenderService.byProject(projectId));
  }

  // ---- helpers ----

  /**
   * Existence and membership in one step. {@code PROJECT:VIEW} says the user may look at projects;
   * it doesn't say which — that's {@code project_members}.
   */
  private ProjectEntity requireAccessibleProject(Long projectId) {
    ProjectEntity project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
    AuthenticatedUser user = CurrentUser.get();
    if (!accessService.seesAllProjects(user) && !accessService.canAccessProject(user, projectId)) {
      throw new AccessDeniedException("You don't have access to project " + projectId + ".");
    }
    return project;
  }

  private static boolean can(String authority) {
    List<String> permissions = CurrentUser.get().permissions();
    return permissions != null && permissions.contains(authority);
  }

  private static LocalDate parseOr(String raw, LocalDate fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return LocalDate.parse(raw.trim());
    } catch (RuntimeException ex) {
      return fallback;
    }
  }
}
