package com.hitech.erp.config;

import com.hitech.erp.project.db.ProjectEntity;
import com.hitech.erp.project.db.ProjectMemberEntity;
import com.hitech.erp.project.db.ProjectMemberRepository;
import com.hitech.erp.project.db.ProjectRepository;
import com.hitech.erp.project.db.ProjectStatus;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the "1 Super Admin → 2 PMs (3 projects each) → 10 Team Members" org used to demo/test the
 * role-hierarchy scoping in Taskopad. Idempotent — safe to run on every boot.
 *
 * <p>Runs after {@link BootstrapAdminRunner} so Super Admin exists first. Uses fixed emails +
 * password {@code Test@1234} so the browser test scripts stay stable.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoDataRunner implements CommandLineRunner {

  private static final String SUPER_ADMIN = "Super Admin";
  private static final String PM_ROLE = "Project Manager";
  private static final String TM_ROLE = "Team Member";
  private static final String OFFICE_MGR_ROLE = "Office Manager";
  private static final String HR_ROLE = "HR Executive";
  private static final String DEMO_PASSWORD = "Test@1234";

  /** Off by default — see the note in {@link #run}. Flip on only for a throwaway test database. */
  @Value("${hitech.demo-data.projects:false}")
  private boolean seedDemoProjects;

  private static final Set<String> PM_PERMS =
      Set.of(
          "DASHBOARD:VIEW", "REPORT:VIEW",
          "PROJECT:VIEW", "PROJECT:CREATE", "PROJECT:EDIT",
          "TASKOPAD:VIEW", "TASKOPAD:CREATE", "TASKOPAD:EDIT", "TASKOPAD:DELETE");

  private static final Set<String> TM_PERMS =
      Set.of(
          "DASHBOARD:VIEW", "REPORT:VIEW",
          "PROJECT:VIEW", "PROJECT:CREATE", "PROJECT:EDIT",
          "TASKOPAD:VIEW", "TASKOPAD:CREATE", "TASKOPAD:EDIT");

  // Office roles get PROJECT:VIEW (read-only) so the task drawer can list projects to tag on
  // office tasks. Tagging is metadata only — the Office rule ignores projectId for access.
  private static final Set<String> OFFICE_MGR_PERMS =
      Set.of(
          "DASHBOARD:VIEW", "PROJECT:VIEW",
          "TASKOPAD:VIEW", "TASKOPAD:CREATE", "TASKOPAD:EDIT", "TASKOPAD:DELETE");

  private static final Set<String> HR_PERMS =
      Set.of(
          "DASHBOARD:VIEW", "PROJECT:VIEW",
          "TASKOPAD:VIEW", "TASKOPAD:CREATE", "TASKOPAD:EDIT");

  private record DemoUser(String email, String fullName, String role, String staffType) {
    DemoUser(String email, String fullName, String role) {
      this(email, fullName, role, "SITE");
    }
  }

  private static final DemoUser[] DEMO_USERS = {
    new DemoUser("pm1@hitech.local", "Ramesh (PM 1)", PM_ROLE),
    new DemoUser("pm2@hitech.local", "Suresh (PM 2)", PM_ROLE),
    new DemoUser("tm1@hitech.local", "Team Member 1", TM_ROLE),
    new DemoUser("tm2@hitech.local", "Team Member 2", TM_ROLE),
    new DemoUser("tm3@hitech.local", "Team Member 3", TM_ROLE),
    new DemoUser("tm4@hitech.local", "Team Member 4", TM_ROLE),
    new DemoUser("tm5@hitech.local", "Team Member 5", TM_ROLE),
    new DemoUser("tm6@hitech.local", "Team Member 6", TM_ROLE),
    new DemoUser("tm7@hitech.local", "Team Member 7", TM_ROLE),
    new DemoUser("tm8@hitech.local", "Team Member 8", TM_ROLE),
    new DemoUser("tm9@hitech.local", "Team Member 9", TM_ROLE),
    new DemoUser("tm10@hitech.local", "Team Member 10", TM_ROLE),
    new DemoUser("office_mgr@hitech.local", "Office Manager (Priya)", OFFICE_MGR_ROLE, "OFFICE"),
    new DemoUser("hr1@hitech.local", "HR Executive 1", HR_ROLE, "OFFICE"),
    new DemoUser("hr2@hitech.local", "HR Executive 2", HR_ROLE, "OFFICE"),
  };

  // Project layout for the hierarchy demo. Names are prefixed "Demo · " so the seeded set is easy
  // to spot and doesn't collide with the real 19 Hi-Tech projects.
  private static final String[] DEMO_PROJECT_LABELS = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"};

  // Memberships per demo project: pm1 owns A-F, pm2 owns G-L; TMs 1-5 sit under pm1 in different
  // slices of A-F, TMs 6-10 sit under pm2 in different slices of G-L.
  private static final Map<String, String[]> PROJECT_MEMBERS = new LinkedHashMap<>();

  static {
    PROJECT_MEMBERS.put("A", new String[] {"pm1", "tm1", "tm2", "tm3"});
    PROJECT_MEMBERS.put("B", new String[] {"pm1", "tm1", "tm2", "tm3"});
    PROJECT_MEMBERS.put("C", new String[] {"pm1", "tm1", "tm2", "tm3"});
    PROJECT_MEMBERS.put("D", new String[] {"pm1", "tm4", "tm5"});
    PROJECT_MEMBERS.put("E", new String[] {"pm1", "tm4", "tm5"});
    PROJECT_MEMBERS.put("F", new String[] {"pm1", "tm4", "tm5"});
    PROJECT_MEMBERS.put("G", new String[] {"pm2", "tm6", "tm7", "tm8"});
    PROJECT_MEMBERS.put("H", new String[] {"pm2", "tm6", "tm7", "tm8"});
    PROJECT_MEMBERS.put("I", new String[] {"pm2", "tm6", "tm7", "tm8"});
    PROJECT_MEMBERS.put("J", new String[] {"pm2", "tm9", "tm10"});
    PROJECT_MEMBERS.put("K", new String[] {"pm2", "tm9", "tm10"});
    PROJECT_MEMBERS.put("L", new String[] {"pm2", "tm9", "tm10"});
  }

  private static final String[] TITLES = {
    "Follow up land clearance approval", "Submit concrete sample to GERI lab",
    "Collect final bill documents", "Diesel stock reorder for site store",
    "Prepare running bill for RMC phase 2", "Site safety audit and report",
    "Verify pipe laying measurement sheet", "Client meeting - elevation drawing signoff",
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
    RoleEntity superAdmin =
        roleRepository
            .findByNameIgnoreCase(SUPER_ADMIN)
            .orElseThrow(() -> new IllegalStateException("Super Admin role missing — check bootstrap"));
    RoleEntity pmRole = ensureRole(PM_ROLE, PM_PERMS, superAdmin.getId());
    RoleEntity tmRole = ensureRole(TM_ROLE, TM_PERMS, pmRole.getId());
    // Office branch is a sibling of the Site (PM) branch, both reporting to Super Admin — so a PM
    // never sees office tasks through their subtree, and vice versa.
    RoleEntity officeMgrRole = ensureRole(OFFICE_MGR_ROLE, OFFICE_MGR_PERMS, superAdmin.getId());
    RoleEntity hrRole = ensureRole(HR_ROLE, HR_PERMS, officeMgrRole.getId());

    Map<String, RoleEntity> rolesByName = new LinkedHashMap<>();
    rolesByName.put(PM_ROLE, pmRole);
    rolesByName.put(TM_ROLE, tmRole);
    rolesByName.put(OFFICE_MGR_ROLE, officeMgrRole);
    rolesByName.put(HR_ROLE, hrRole);

    Map<String, AppUserEntity> byShort = ensureUsers(rolesByName);

    // The demo *projects* are off by default. They were being recreated on every boot, so deleting
    // them from the client's books only ever lasted until the next restart — and once real work is
    // in the system, twelve "Demo · Project" entries in the project picker are noise, not a demo.
    // Roles and users above still seed: the role hierarchy is live behaviour the app depends on,
    // not sample data. Set `hitech.demo-data.projects=true` to get the sample org back for testing.
    if (!seedDemoProjects) {
      log.info("DemoDataRunner: demo projects disabled (hitech.demo-data.projects=false)");
      return;
    }
    Map<String, ProjectEntity> demoProjects = ensureDemoProjects();
    ensureMemberships(demoProjects, byShort);
    ensureTasks(demoProjects, byShort);
    ensureOfficeTasks(demoProjects, byShort);
  }

  /** Create the role if missing; always fix its reports_to and top up its permissions. */
  private RoleEntity ensureRole(String name, Set<String> permCodes, Long parentId) {
    RoleEntity role =
        roleRepository
            .findByNameIgnoreCase(name)
            .orElseGet(
                () -> {
                  RoleEntity fresh = new RoleEntity();
                  fresh.setName(name);
                  fresh.setDescription(name + " (seeded)");
                  fresh.setSystem(false);
                  fresh.setPermissions(new HashSet<>());
                  log.info("DemoDataRunner: created role '{}'", name);
                  return roleRepository.save(fresh);
                });

    boolean dirty = false;
    if (parentId != null && !parentId.equals(role.getReportsToRoleId())) {
      role.setReportsToRoleId(parentId);
      dirty = true;
    }
    Set<PermissionEntity> resolved =
        permissionRepository.findAll().stream()
            .filter(p -> permCodes.contains(p.getCode()))
            .collect(Collectors.toSet());
    if (!role.getPermissions().containsAll(resolved)) {
      Set<PermissionEntity> merged = new HashSet<>(role.getPermissions());
      merged.addAll(resolved);
      role.setPermissions(merged);
      dirty = true;
    }
    if (dirty) roleRepository.save(role);
    return role;
  }

  /** Return the demo users keyed by short handle (pm1, tm3, …), creating any missing ones. */
  private Map<String, AppUserEntity> ensureUsers(Map<String, RoleEntity> rolesByName) {
    Map<String, AppUserEntity> out = new LinkedHashMap<>();
    for (DemoUser du : DEMO_USERS) {
      RoleEntity target = rolesByName.get(du.role());
      if (target == null) {
        throw new IllegalStateException("Missing role in seed map: " + du.role());
      }
      AppUserEntity user =
          userRepository
              .findByEmailIgnoreCase(du.email())
              .orElseGet(
                  () -> {
                    AppUserEntity e = new AppUserEntity();
                    e.setEmail(du.email());
                    e.setFullName(du.fullName());
                    e.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
                    e.setActive(true);
                    e.setRole(target);
                    e.setStaffType(du.staffType());
                    log.info(
                        "DemoDataRunner: created demo user [{}] ({} / {})",
                        du.email(),
                        du.role(),
                        du.staffType());
                    return userRepository.save(e);
                  });
      // Keep role + staffType in sync in case someone flipped them in the admin UI.
      boolean dirty = false;
      if (user.getRole() == null || !target.getId().equals(user.getRole().getId())) {
        user.setRole(target);
        dirty = true;
      }
      if (!du.staffType().equalsIgnoreCase(user.getStaffType())) {
        user.setStaffType(du.staffType());
        dirty = true;
      }
      if (dirty) userRepository.save(user);
      String shortHandle = du.email().substring(0, du.email().indexOf('@'));
      out.put(shortHandle, user);
    }
    return out;
  }

  /** Create the "Demo · <label>" projects if not already there. */
  private Map<String, ProjectEntity> ensureDemoProjects() {
    Map<String, ProjectEntity> byLabel = new LinkedHashMap<>();
    for (String label : DEMO_PROJECT_LABELS) {
      String name = "Demo · Project " + label;
      ProjectEntity project =
          projectRepository.findAll().stream()
              .filter(p -> name.equalsIgnoreCase(p.getName()))
              .findFirst()
              .orElseGet(
                  () -> {
                    ProjectEntity fresh = new ProjectEntity();
                    fresh.setName(name);
                    fresh.setProjectCode("DEMO-" + label);
                    fresh.setCategory("Demo");
                    fresh.setStatus(ProjectStatus.ONGOING);
                    fresh.setCity("Ahmedabad");
                    fresh.setCompanyBranch("Hi-Tech Demo");
                    fresh.setStartDate(LocalDate.now().minusDays(30).toString());
                    fresh.setEndDate(LocalDate.now().plusDays(90).toString());
                    log.info("DemoDataRunner: created demo project '{}'", name);
                    return projectRepository.save(fresh);
                  });
      byLabel.put(label, project);
    }
    return byLabel;
  }

  private void ensureMemberships(
      Map<String, ProjectEntity> demoProjects, Map<String, AppUserEntity> users) {
    for (Map.Entry<String, String[]> entry : PROJECT_MEMBERS.entrySet()) {
      ProjectEntity project = demoProjects.get(entry.getKey());
      if (project == null) continue;
      Set<Long> already =
          new HashSet<>(memberRepository.findUserIdsByProjectId(project.getId()));
      for (String handle : entry.getValue()) {
        AppUserEntity u = users.get(handle);
        if (u == null || already.contains(u.getId())) continue;
        ProjectMemberEntity m = new ProjectMemberEntity();
        m.setProjectId(project.getId());
        m.setUserId(u.getId());
        memberRepository.save(m);
      }
    }
  }

  /**
   * Seed one task per project per member (so pm1 has some, each tm has some, and pm1's "All"
   * expands to cover their TMs' work). Only runs when the task table is empty on a demo project so
   * we don't drown a populated tenant.
   */
  private void ensureTasks(Map<String, ProjectEntity> demoProjects, Map<String, AppUserEntity> users) {
    List<Long> demoProjectIds = demoProjects.values().stream().map(ProjectEntity::getId).toList();
    long existing =
        taskRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(t -> demoProjectIds.contains(t.getProjectId()))
            .count();
    if (existing > 0) return;

    LocalDate today = LocalDate.now();
    int titleIdx = 0;
    int code = 2001;
    for (Map.Entry<String, String[]> entry : PROJECT_MEMBERS.entrySet()) {
      ProjectEntity project = demoProjects.get(entry.getKey());
      if (project == null) continue;
      for (String handle : entry.getValue()) {
        AppUserEntity u = users.get(handle);
        if (u == null) continue;
        TaskEntity t = new TaskEntity();
        t.setCode("T-" + code++);
        t.setTitle(project.getName() + " · " + TITLES[titleIdx++ % TITLES.length]);
        t.setDescription("Seeded demo task for " + u.getFullName() + " on " + project.getName());
        t.setProjectId(project.getId());
        t.setAssigneeId(u.getId());
        t.setCreatedBy(u.getId());
        t.setStatus(TaskStatus.PENDING);
        t.setPriority(TaskPriority.MEDIUM);
        t.setProgress(0);
        t.setDueDate(today.plusDays(7).toString());
        t.setDraft(false);
        Set<Long> followers = new HashSet<>();
        followers.add(u.getId()); // creator follows their own task
        t.setFollowerIds(followers);
        t.logActivity(u.getId(), "Task created");
        taskRepository.save(t);
      }
    }
    log.info("DemoDataRunner: seeded demo tasks across {} demo projects", demoProjects.size());
  }

  /**
   * Office-branch tasks: a few with no project (pure office work) and one tagged with Project A —
   * the "just for info" scenario. Under the Office rule, the tagged task is visible to office_mgr
   * (subtree hit, project intersection skipped for Office assignees) but NOT to pm1 (pm1 is in A
   * but hr1 isn't in pm1's role subtree).
   */
  private void ensureOfficeTasks(
      Map<String, ProjectEntity> demoProjects, Map<String, AppUserEntity> users) {
    AppUserEntity officeMgr = users.get("office_mgr");
    AppUserEntity hr1 = users.get("hr1");
    AppUserEntity hr2 = users.get("hr2");
    if (officeMgr == null || hr1 == null || hr2 == null) return;

    Long projectA = demoProjects.get("A") != null ? demoProjects.get("A").getId() : null;

    // Idempotent: only seed if none of these three assignees already have any office task.
    Set<Long> officeIds =
        new HashSet<>(java.util.Arrays.asList(officeMgr.getId(), hr1.getId(), hr2.getId()));
    long already =
        taskRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(t -> officeIds.contains(t.getAssigneeId()))
            .count();
    if (already > 0) return;

    LocalDate due = LocalDate.now().plusDays(7);
    int code = 5001;

    record Seed(AppUserEntity assignee, String title, Long projectId, String note) {}
    List<Seed> seeds =
        List.of(
            new Seed(officeMgr, "Office · Approve July payroll batch", null, "Own office work"),
            new Seed(hr1, "Office · Draft offer letter for new civil engineer", null, "No project"),
            new Seed(hr1, "Office · Site allowance policy note — for Project A", projectA,
                "Tagged with Project A for info; hr1 is not a Project A member"),
            new Seed(hr2, "Office · Update PF nomination register", null, "No project"));

    for (Seed s : seeds) {
      TaskEntity t = new TaskEntity();
      t.setCode("T-" + code++);
      t.setTitle(s.title());
      t.setDescription(s.note());
      t.setProjectId(s.projectId());
      t.setAssigneeId(s.assignee().getId());
      t.setCreatedBy(s.assignee().getId());
      t.setStatus(TaskStatus.PENDING);
      t.setPriority(TaskPriority.MEDIUM);
      t.setProgress(0);
      t.setDueDate(due.toString());
      t.setDraft(false);
      Set<Long> followers = new HashSet<>();
      followers.add(s.assignee().getId());
      t.setFollowerIds(followers);
      t.logActivity(s.assignee().getId(), "Task created");
      taskRepository.save(t);
    }
    log.info("DemoDataRunner: seeded {} office-branch tasks", seeds.size());
  }
}
