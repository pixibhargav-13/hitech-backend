package com.hitech.erp.usermanagement.access;

import java.util.Set;

/**
 * The set of projects a single request is allowed to read, already narrowed by any {@code projectId}
 * the caller asked for. Produced by {@link ProjectScopeResolver}; consumed by every module that
 * stores a {@code project_id}.
 *
 * <p>Two independent dimensions are folded into one object:
 *
 * <ul>
 *   <li>{@code allowed} — what the signed-in user <em>may</em> see. {@code null} means "everything"
 *       (Super Admin, or an Office member who isn't project-scoped by design).
 *   <li>{@code filter} — what the caller <em>asked</em> to see. {@code null} means "all of the
 *       above". A non-null filter has already been checked against {@code allowed} by the resolver,
 *       so by the time a scope exists the request is known to be legitimate.
 * </ul>
 *
 * <p>Records with a {@code null} project id are shared/office overhead — visible to unrestricted
 * users only, and never to a site member who is limited to their own sites.
 */
public record ProjectScope(Set<Long> allowed, Long filter) {

  /** No restriction and no filter — see every record, project-tagged or not. */
  public static ProjectScope everything() {
    return new ProjectScope(null, null);
  }

  /** Unrestricted user who asked for one project. */
  public static ProjectScope everythingFilteredTo(Long projectId) {
    return new ProjectScope(null, projectId);
  }

  /** Restricted user, no filter — their whole accessible set. */
  public static ProjectScope limitedTo(Set<Long> allowed) {
    return new ProjectScope(allowed, null);
  }

  /** True when this scope imposes no restriction at all — lets callers skip per-row filtering. */
  public boolean isEverything() {
    return allowed == null && filter == null;
  }

  /** True when the caller narrowed the request to a single project. */
  public boolean isSingleProject() {
    return filter != null;
  }

  /** True when a record carrying {@code recordProjectId} belongs in this request's results. */
  public boolean matches(Long recordProjectId) {
    if (filter != null) return filter.equals(recordProjectId);
    if (allowed == null) return true;
    return recordProjectId != null && allowed.contains(recordProjectId);
  }
}
