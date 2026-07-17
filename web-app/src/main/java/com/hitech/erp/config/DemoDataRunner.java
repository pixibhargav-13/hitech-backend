package com.hitech.erp.config;

import com.hitech.erp.project.db.ProjectEntity;
import com.hitech.erp.project.db.ProjectMemberEntity;
import com.hitech.erp.project.db.ProjectMemberRepository;
import com.hitech.erp.project.db.ProjectRepository;
import com.hitech.erp.task.db.TaskEntity;
import com.hitech.erp.task.db.TaskPriority;
import com.hitech.erp.task.db.TaskRepository;
import com.hitech.erp.task.db.TaskStatus;
import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import com.hitech.erp.usermanagement.db.PermissionEntity;
import com.hitech.erp.usermanagement.db.PermissionRepository;
import com.hitech.erp.usermanagement.db.RoleEntity;
import com.hitech.erp.usermanagement.db.RoleRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a realistic team, their project memberships and a spread of Taskopad tasks so the module is
 * populated from the database (and access-control is demonstrable) on a fresh install. Idempotent —
 * only fills gaps. Runs after {@link BootstrapAdminRunner} so the admin exists.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoDataRunner implements CommandLineRunner {

  private static final String TEAM_ROLE = "Team Member";
  private static final String DEMO_PASSWORD = "Password@123";

  // Team-member permission codes — enough to use Taskopad and see their projects.
  private static final Set<String> TEAM_PERMS =
      Set.of(
          "DASHBOARD:VIEW", "REPORT:VIEW",
          "PROJECT:VIEW", "PROJECT:CREATE", "PROJECT:EDIT",
          "TASKOPAD:VIEW", "TASKOPAD:CREATE", "TASKOPAD:EDIT");

  private static final String[][] DEMO_USERS = {
    {"vishwas@hitech.local", "Vishwas Bhai Ujjain"},
    {"ketan@hitech.local", "Ketan Bhai Vaghela"},
    {"mahesh@hitech.local", "Mahesh Bhai Chauhan"},
    {"jignesh@hitech.local", "Jignesh Parmar"},
  };

  private static final String[] TITLES = {
    "Follow up land clearance approval", "Submit concrete sample to GERI lab",
    "Collect final bill documents", "Diesel stock reorder for site store",
    "Prepare running bill for RMC phase 2", "Site safety audit and report",
    "Verify pipe laying measurement sheet", "Client meeting - elevation drawing signoff",
    "Reconcile material issue register", "Update BOQ against actual execution",
    "Arrange JCB for excavation work", "Chase pending work order from RMC",
    "Quality check - MH construction", "Prepare monthly progress report",
    "Vendor payment follow-up", "Update attendance register for site crew",
    "Coordinate road cutting permission", "Inspect shuttering before pour",
  };

  private static final String[] DESCRIPTIONS = {
    "Coordinate with the department and share the updated status by end of day.",
    "Attach the supporting documents once the site team confirms measurements.",
    "Blocked on approval — escalate if not cleared by the due date.",
    "Routine check, capture photos and upload them against this task.",
    "",
  };

  private final AppUserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final ProjectRepository projectRepository;
  private final ProjectMemberRepository memberRepository;
  private final TaskRepository taskRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public void run(String... args) {
    RoleEntity teamRole = ensureTeamRole();
    List<AppUserEntity> team = ensureDemoUsers(teamRole);
    if (team.isEmpty()) return;

    List<ProjectEntity> projects = projectRepository.findAll();
    if (projects.isEmpty()) {
      log.info("DemoDataRunner: no projects yet — skipping memberships/tasks seed.");
      return;
    }

    ensureMemberships(team, projects);
    ensureTasks(team, projects);
  }

  private RoleEntity ensureTeamRole() {
    return roleRepository
        .findByNameIgnoreCase(TEAM_ROLE)
        .orElseGet(
            () -> {
              Set<PermissionEntity> perms =
                  permissionRepository.findAll().stream()
                      .filter(p -> TEAM_PERMS.contains(p.getCode()))
                      .collect(Collectors.toSet());
              RoleEntity role = new RoleEntity();
              role.setName(TEAM_ROLE);
              role.setDescription("Site/office team member — Taskopad access to assigned projects");
              role.setSystem(false);
              role.setPermissions(perms);
              log.info("Seeded '{}' role with {} permissions", TEAM_ROLE, perms.size());
              return roleRepository.save(role);
            });
  }

  private List<AppUserEntity> ensureDemoUsers(RoleEntity role) {
    List<AppUserEntity> team = new ArrayList<>();
    for (String[] u : DEMO_USERS) {
      String email = u[0];
      AppUserEntity user =
          userRepository
              .findByEmailIgnoreCase(email)
              .orElseGet(
                  () -> {
                    AppUserEntity e = new AppUserEntity();
                    e.setEmail(email);
                    e.setFullName(u[1]);
                    e.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
                    e.setActive(true);
                    e.setRole(role);
                    log.info("Seeded demo team member [{}]", email);
                    return userRepository.save(e);
                  });
      team.add(user);
    }
    return team;
  }

  private void ensureMemberships(List<AppUserEntity> team, List<ProjectEntity> projects) {
    Random rng = new Random(42);
    for (AppUserEntity user : team) {
      List<Long> existing = memberRepository.findProjectIdsByUserId(user.getId());
      if (!existing.isEmpty()) continue;
      // Each member gets a deterministic slice (~half) of the projects.
      List<ProjectEntity> shuffled = new ArrayList<>(projects);
      java.util.Collections.shuffle(shuffled, rng);
      int take = Math.max(4, projects.size() / 2);
      shuffled.stream()
          .limit(take)
          .forEach(
              p -> {
                ProjectMemberEntity m = new ProjectMemberEntity();
                m.setProjectId(p.getId());
                m.setUserId(user.getId());
                memberRepository.save(m);
              });
    }
  }

  private void ensureTasks(List<AppUserEntity> team, List<ProjectEntity> projects) {
    if (taskRepository.count() > 0) return;

    List<Long> assigneeIds = new ArrayList<>(team.stream().map(AppUserEntity::getId).toList());
    userRepository
        .findByEmailIgnoreCase("admin@hitech.local")
        .ifPresent(a -> assigneeIds.add(a.getId()));

    TaskStatus[] statuses = TaskStatus.values();
    TaskPriority[] priorities = TaskPriority.values();
    Random rng = new Random(7);
    LocalDate today = LocalDate.now();
    int code = 1001;

    for (int i = 0; i < 24; i++) {
      TaskStatus status = statuses[rng.nextInt(statuses.length)];
      TaskPriority priority = priorities[rng.nextInt(priorities.length)];
      Long assignee = assigneeIds.get(rng.nextInt(assigneeIds.size()));
      // Spread due dates -10..+20 days so "due today"/"overdue" buckets populate.
      LocalDate due = today.plusDays(rng.nextInt(31) - 10);
      int progress =
          status == TaskStatus.COMPLETED ? 100 : status == TaskStatus.PENDING ? 0 : rng.nextInt(90);

      TaskEntity t = new TaskEntity();
      t.setCode("T-" + code++);
      t.setTitle(TITLES[i % TITLES.length]);
      t.setDescription(DESCRIPTIONS[rng.nextInt(DESCRIPTIONS.length)]);
      t.setProjectId(projects.get(rng.nextInt(projects.size())).getId());
      t.setAssigneeId(assignee);
      t.setCreatedBy(assignee);
      t.setStatus(status);
      t.setPriority(priority);
      t.setProgress(progress);
      t.setDueDate(due.toString());
      t.setDraft(false);

      Set<Long> followers = new HashSet<>();
      if (rng.nextBoolean()) followers.add(assigneeIds.get(rng.nextInt(assigneeIds.size())));
      t.setFollowerIds(followers);

      t.logActivity(assignee, "Task created");
      taskRepository.save(t);
    }
    log.info("Seeded 24 demo tasks");
  }
}
