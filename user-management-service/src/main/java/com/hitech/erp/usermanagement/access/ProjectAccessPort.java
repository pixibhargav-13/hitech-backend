package com.hitech.erp.usermanagement.access;

import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import java.util.List;

/**
 * What a module needs to know about project membership, without depending on the project module.
 *
 * <p>{@code project_members} lives in {@code project-service}, but Vyapar, Payroll and Tender all
 * store a {@code project_id} and therefore all need to answer "may this user see this project?".
 * Sibling services deliberately don't depend on each other, so the question is asked through this
 * interface — declared here in {@code user-management-service} (which every module already depends
 * on) and implemented by the project module at runtime.
 */
public interface ProjectAccessPort {

  /**
   * True when the user isn't project-scoped at all. Covers Super Admin and Office-typed members —
   * an accountant has to see every project's invoices, and Office members hold no project
   * membership rows, so intersecting them would always return empty.
   */
  boolean seesAllProjects(AuthenticatedUser user);

  /** Project ids the user is a member of. An empty list is meaningful: no projects. */
  List<Long> accessibleProjectIds(AuthenticatedUser user);
}
