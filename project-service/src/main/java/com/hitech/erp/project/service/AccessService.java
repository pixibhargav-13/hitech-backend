package com.hitech.erp.project.service;

import com.hitech.erp.project.db.ProjectMemberRepository;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import com.hitech.erp.usermanagement.db.RoleRepository;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central place that answers "what can this user see?". Super Admin sees everything; everyone else
 * is restricted to the projects they're a member of. Also exposes the reports-to role subtree so
 * managers can widen their view to their direct/indirect reports' work. Shared by the project and
 * task modules.
 */
@Service
@RequiredArgsConstructor
public class AccessService {

  public static final String SUPER_ADMIN = "Super Admin";

  private final ProjectMemberRepository memberRepository;
  private final RoleRepository roleRepository;
  private final AppUserRepository userRepository;

  /** Super Admin (or a role wired to every permission) is never project-restricted. */
  public boolean seesEverything(AuthenticatedUser user) {
    return user != null && SUPER_ADMIN.equalsIgnoreCase(user.roleName());
  }

  /**
   * Office members aren't project-scoped by design (they don't punch on-site or take a project
   * membership), so intersecting them with {@code project_members} would always return empty. When
   * they hold {@code PROJECT:VIEW}, treat them like Super Admin for the projects list — they get
   * the full read-only catalogue, mostly so the task drawer can offer any project to tag on an
   * office task for reference. Site members stay strictly membership-scoped.
   */
  @Transactional(readOnly = true)
  public boolean isProjectListUnscoped(AuthenticatedUser user) {
    if (user == null) return false;
    if (seesEverything(user)) return true;
    return userRepository
        .findById(user.id())
        .map(u -> "OFFICE".equalsIgnoreCase(u.getStaffType()))
        .orElse(false);
  }

  /** Project ids the user may access. Empty list is meaningful (= no projects) for non-admins. */
  @Transactional(readOnly = true)
  public List<Long> accessibleProjectIds(AuthenticatedUser user) {
    return memberRepository.findProjectIdsByUserId(user.id());
  }

  @Transactional(readOnly = true)
  public boolean canAccessProject(AuthenticatedUser user, Long projectId) {
    if (seesEverything(user)) return true;
    return projectId != null && memberRepository.existsByProjectIdAndUserId(projectId, user.id());
  }

  /**
   * Every role that transitively reports to {@code rootRoleId} (children, grandchildren, …). Does
   * NOT include the root itself. Cycle-guarded — data-corrupt loops throw instead of spinning.
   */
  @Transactional(readOnly = true)
  public Set<Long> descendantRoleIds(Long rootRoleId) {
    Set<Long> out = new LinkedHashSet<>();
    if (rootRoleId == null) return out;
    Deque<Long> stack = new ArrayDeque<>();
    stack.push(rootRoleId);
    Set<Long> visited = new HashSet<>();
    int guard = 0;
    while (!stack.isEmpty()) {
      Long cur = stack.pop();
      if (!visited.add(cur)) continue;
      if (++guard > 1000) {
        throw new IllegalStateException("Role hierarchy is too deep or contains a loop");
      }
      for (Long child : roleRepository.findChildRoleIds(cur)) {
        if (visited.contains(child)) continue;
        out.add(child);
        stack.push(child);
      }
    }
    return out;
  }

  /** Users sitting anywhere below this user in the role ladder (excludes the user themselves). */
  @Transactional(readOnly = true)
  public Set<Long> subtreeUserIds(AuthenticatedUser user) {
    if (user == null || user.roleId() == null) return Set.of();
    Set<Long> roles = descendantRoleIds(user.roleId());
    if (roles.isEmpty()) return Set.of();
    return new HashSet<>(userRepository.findIdsByRoleIdIn(roles));
  }

  /**
   * The Office-typed slice of a given user set. Office members aren't project-scoped, so their
   * tasks flow up the role tree on identity alone — this set lets callers skip the project
   * intersection for those assignees while keeping it strict for Site assignees.
   */
  @Transactional(readOnly = true)
  public Set<Long> officeIdsAmong(Set<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) return Set.of();
    return new HashSet<>(userRepository.findOfficeIdsAmong(userIds));
  }

  /** True when the user has anyone reporting up to them (or is Super Admin). */
  @Transactional(readOnly = true)
  public boolean hasSubtree(AuthenticatedUser user) {
    if (seesEverything(user)) return true;
    return !subtreeUserIds(user).isEmpty();
  }
}
