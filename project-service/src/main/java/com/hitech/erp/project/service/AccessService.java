package com.hitech.erp.project.service;

import com.hitech.erp.project.db.ProjectMemberRepository;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central place that answers "what can this user see?". Super Admin sees everything; everyone else
 * is restricted to the projects they're a member of (and, for tasks, their own assigned/followed
 * work). Shared by the project and task modules.
 */
@Service
@RequiredArgsConstructor
public class AccessService {

  public static final String SUPER_ADMIN = "Super Admin";

  private final ProjectMemberRepository memberRepository;

  /** Super Admin (or a role wired to every permission) is never project-restricted. */
  public boolean seesEverything(AuthenticatedUser user) {
    return user != null && SUPER_ADMIN.equalsIgnoreCase(user.roleName());
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
}
