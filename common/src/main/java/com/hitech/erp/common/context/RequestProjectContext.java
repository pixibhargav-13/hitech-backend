package com.hitech.erp.common.context;

/**
 * The project a request turned out to be about, so the audit trail can file it under that project.
 *
 * <p>The audit filter sees only the URL, which is enough for {@code /projects/42/locations} or a
 * {@code ?projectId=42} query, but not for the interesting cases: a sales invoice carries its
 * project in the request <em>body</em>, and parsing bodies in a filter would mean buffering every
 * upload in the system to find one number.
 *
 * <p>So the module that already parsed the body stamps it here instead. {@link #set} is called deep
 * in the service where the project id is known for certain; the audit filter reads it on the way out
 * and always clears it, because the thread goes back into the pool.
 */
public final class RequestProjectContext {

  private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

  private RequestProjectContext() {}

  /** Record that this request concerns a project. Null is ignored, so callers needn't check. */
  public static void set(Long projectId) {
    if (projectId != null) CURRENT.set(projectId);
  }

  /** The project stamped on this request, or null. */
  public static Long get() {
    return CURRENT.get();
  }

  /** Must be called at the end of every request — thread pools outlive requests. */
  public static void clear() {
    CURRENT.remove();
  }
}
