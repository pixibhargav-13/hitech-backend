package com.hitech.erp.workspace;

import com.hitech.erp.payroll.dto.PayrollDtos.ProjectManpower;
import com.hitech.erp.task.dto.TaskDtos.ProjectWorkload;
import com.hitech.erp.tender.dto.TenderDtos.TenderResponse;
import com.hitech.erp.vyapar.dto.VyaparDtos.ProjectFinance;
import java.util.List;

/** Wire shapes for the composed Project workspace endpoints. */
public final class ProjectWorkspaceDtos {

  private ProjectWorkspaceDtos() {}

  /**
   * Everything the Project workspace Dashboard shows, in one call, derived from the modules that
   * own the underlying records.
   *
   * <p>Sections are null when the caller lacks that module's {@code :VIEW} permission — a site
   * supervisor with no Vyapar access gets manpower and tasks but no money, rather than a 403 for
   * the whole page.
   */
  public record ProjectSummary(
      Long projectId,
      ProjectFinance finance,
      ProjectWorkload tasks,
      ProjectManpower manpower,
      List<TenderResponse> tenders,
      ProjectProgress progress,
      /** Window the manpower figures cover, echoed back so the UI can label its chart. */
      String from,
      String to) {}

  /**
   * The project's progress percentage alongside the number the data implies.
   *
   * <p>{@code reported} is the site manager's judgement (the editable field on the project) —
   * deliberately kept, because percent-complete on a construction site is not something task counts
   * can decide. {@code derivedFromTasks} is shown next to it so a project claiming 90% with half
   * its tasks open is visible as such instead of quietly passing.
   */
  public record ProjectProgress(int reported, int derivedFromTasks, boolean diverges) {}
}
