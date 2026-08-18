package com.hitech.erp.usermanagement.access;

import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Turns the {@code projectId} query parameter on a request into an enforced {@link ProjectScope}.
 *
 * <p>Before this existed, every module treated {@code projectId} as a display filter: pass it and
 * you got that project, omit it and you got everything. A site member holding {@code VYAPAR:VIEW}
 * could therefore read any project's money simply by leaving the parameter off. Passing a project
 * id is now a request, not a grant — the resolver checks it against the caller's membership and
 * rejects anything they aren't on, and an absent id narrows to their own projects instead of
 * widening to all.
 */
@Component
public class ProjectScopeResolver {

  private final ObjectProvider<ProjectAccessPort> accessPort;

  public ProjectScopeResolver(ObjectProvider<ProjectAccessPort> accessPort) {
    this.accessPort = accessPort;
  }

  /**
   * The scope for the current request.
   *
   * @param requestedProjectId the caller's {@code projectId} parameter, or null for "no filter"
   * @throws AccessDeniedException when the caller asked for a project they're not a member of
   */
  public ProjectScope resolve(Long requestedProjectId) {
    ProjectAccessPort port = accessPort.getIfAvailable();
    AuthenticatedUser user = currentUser();

    // No project module on the classpath (module-level tests) or no principal (internal call):
    // behave exactly as the code did before scoping existed rather than locking everything out.
    if (port == null || user == null) {
      return requestedProjectId == null
          ? ProjectScope.everything()
          : ProjectScope.everythingFilteredTo(requestedProjectId);
    }

    if (port.seesAllProjects(user)) {
      return requestedProjectId == null
          ? ProjectScope.everything()
          : ProjectScope.everythingFilteredTo(requestedProjectId);
    }

    Set<Long> allowed = new HashSet<>(port.accessibleProjectIds(user));
    if (requestedProjectId == null) {
      return ProjectScope.limitedTo(allowed);
    }
    if (!allowed.contains(requestedProjectId)) {
      throw new AccessDeniedException("You don't have access to project " + requestedProjectId + ".");
    }
    return new ProjectScope(allowed, requestedProjectId);
  }

  /**
   * Guard for writes — a record may only be tagged with a project the caller can reach. A null
   * project id is always allowed: that's a shared/office record, which is what omitting the field
   * has always meant.
   */
  public void assertCanWrite(Long projectId) {
    if (projectId == null) return;
    resolve(projectId);
  }

  /**
   * Project ids to hand to a repository {@code IN (…)} query, or null when the caller is
   * unrestricted and the query needs no project predicate at all. Prefer this over
   * {@link ProjectScope#matches} whenever the filtering can happen in SQL.
   */
  public List<Long> queryableIds(ProjectScope scope) {
    if (scope.filter() != null) return List.of(scope.filter());
    if (scope.allowed() == null) return null;
    return List.copyOf(scope.allowed());
  }

  private static AuthenticatedUser currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) ? u : null;
  }
}
