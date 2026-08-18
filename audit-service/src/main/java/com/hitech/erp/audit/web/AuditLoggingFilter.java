package com.hitech.erp.audit.web;

import com.hitech.erp.audit.db.AuditAction;
import com.hitech.erp.audit.service.AuditRecorder;
import com.hitech.erp.common.context.RequestProjectContext;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Records every state-changing API call. Registered *after* Spring Security's filter chain
 * (see {@link AuditWebConfig}) so the JWT principal is already resolved when we log.
 *
 * <p>Reads are deliberately not audited - they'd swamp the trail with noise and carry no
 * change of state. Login/logout are audited because they are security-relevant.
 */
@RequiredArgsConstructor
public class AuditLoggingFilter extends OncePerRequestFilter {

  private static final String API_PREFIX = "/api/v1/";
  private static final String LOGIN_PATH = "/api/v1/auth/login";
  private static final String LOGOUT_PATH = "/api/v1/auth/logout";
  private static final Pattern EMAIL_IN_BODY = Pattern.compile("\"email\"\\s*:\\s*\"([^\"]+)\"");

  private final AuditRecorder recorder;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path == null || !path.startsWith(API_PREFIX)) return true;
    // Never audit reads, and never audit the audit trail itself.
    if (path.startsWith("/api/v1/audit-logs")) return true;
    return !isMutating(request.getMethod()) && !LOGIN_PATH.equals(path);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String path = request.getRequestURI();
    boolean isLogin = LOGIN_PATH.equals(path);

    // Only the login body is cached - we need the email to attribute the attempt.
    HttpServletRequest effective = isLogin ? new ContentCachingRequestWrapper(request, 4096) : request;

    try {
      chain.doFilter(effective, response);
    } finally {
      try {
        recordFor(effective, response, path, isLogin);
      } catch (Exception ignored) {
        // Auditing must never surface as a request failure.
      } finally {
        // The thread returns to the pool; a leftover stamp would mis-file the next request.
        RequestProjectContext.clear();
      }
    }
  }

  private void recordFor(HttpServletRequest request, HttpServletResponse response, String path, boolean isLogin) {
    AuditAction action = resolveAction(request.getMethod(), path);
    if (action == null) return;

    AuthenticatedUser actor = currentUser();
    String fallbackEmail = isLogin ? emailFromBody(request) : null;
    int status = response.getStatus();

    String entityType = null;
    String entityId = null;
    String summary;

    if (action == AuditAction.LOGIN) {
      boolean ok = status >= 200 && status < 300;
      summary = ok ? "Signed in" : "Sign-in failed";
      entityType = "AUTH";
    } else if (action == AuditAction.LOGOUT) {
      summary = "Signed out";
      entityType = "AUTH";
    } else {
      PathTarget target = parsePath(path);
      entityType = target.entityType();
      entityId = target.entityId();
      summary = describe(action, entityType, entityId, status);
    }

    recorder.record(
        new AuditRecorder.AuditEvent(
            actor,
            fallbackEmail,
            action,
            entityType,
            entityId,
            resolveProjectId(request, path),
            summary,
            request.getMethod(),
            path,
            status,
            clientIp(request),
            request.getHeader("User-Agent")));
  }

  /**
   * Which project this call touched, from the cheapest reliable source first:
   *
   * <ol>
   *   <li>a stamp the handling service left behind ({@link RequestProjectContext}) — the only
   *       source that knows about a project id buried in a request body;
   *   <li>a {@code ?projectId=} query parameter;
   *   <li>the id in a {@code /projects/{id}/…} path.
   * </ol>
   */
  static Long resolveProjectId(HttpServletRequest request, String path) {
    Long stamped = RequestProjectContext.get();
    if (stamped != null) return stamped;

    Long fromQuery = parseLong(request.getParameter("projectId"));
    if (fromQuery != null) return fromQuery;

    return projectIdFromPath(path);
  }

  /** The {@code {id}} in {@code /api/v1/projects/{id}/…}, or null when the path isn't project-scoped. */
  static Long projectIdFromPath(String path) {
    String marker = API_PREFIX + "projects/";
    int at = path.indexOf(marker);
    if (at < 0) return null;
    String rest = path.substring(at + marker.length());
    int slash = rest.indexOf('/');
    return parseLong(slash < 0 ? rest : rest.substring(0, slash));
  }

  private static Long parseLong(String s) {
    if (s == null || s.isBlank() || !isNumeric(s.trim())) return null;
    try {
      return Long.valueOf(s.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static String describe(AuditAction action, String entityType, String entityId, int status) {
    String verb = switch (action) {
          case CREATE -> "Created";
          case UPDATE -> "Updated";
          case DELETE -> "Deleted";
          default -> "Changed";
        };
    String subject = entityType == null ? "record" : entityType.toLowerCase(Locale.ROOT).replace('_', ' ');
    String base = entityId == null ? verb + " " + subject : verb + " " + subject + " #" + entityId;
    return status >= 400 ? base + " (failed)" : base;
  }

  private static AuditAction resolveAction(String method, String path) {
    if (LOGIN_PATH.equals(path)) return AuditAction.LOGIN;
    if (LOGOUT_PATH.equals(path)) return AuditAction.LOGOUT;
    return switch (method.toUpperCase(Locale.ROOT)) {
      case "POST" -> AuditAction.CREATE;
      case "PUT", "PATCH" -> AuditAction.UPDATE;
      case "DELETE" -> AuditAction.DELETE;
      default -> null;
    };
  }

  private static boolean isMutating(String method) {
    String m = method == null ? "" : method.toUpperCase(Locale.ROOT);
    return m.equals("POST") || m.equals("PUT") || m.equals("PATCH") || m.equals("DELETE");
  }

  /**
   * Trailing segments that name the <em>action</em> rather than the thing acted on. Without this,
   * {@code /payroll/attendance/edit} attributes to "EDIT" and the trail reads "Created edit",
   * which says nothing about what changed. Skipping them attributes to ATTENDANCE instead; the
   * action itself is already carried separately on the row.
   */
  private static final java.util.Set<String> ACTION_SEGMENTS =
      java.util.Set.of(
          "edit", "punch", "cancel", "restore", "approve", "reject", "submit", "stage", "status",
          "bulk", "import", "export", "link", "unlink", "reorder", "duplicate", "clone", "range",
          "settings", "refresh", "logout", "login", "complete", "reopen", "assign", "pay", "lock",
          "unlock", "generate", "recalculate");

  /**
   * Derives the target from the URL. Walks the segments so nested resources attribute to the
   * innermost one: /projects/42/locations/7 -> LOCATION #7, /projects/42/locations -> LOCATION.
   * Trailing action verbs are skipped — see {@link #ACTION_SEGMENTS}.
   */
  static PathTarget parsePath(String path) {
    String rest = path.substring(API_PREFIX.length());
    String resource = null;
    String id = null;
    for (String segment : rest.split("/")) {
      if (segment.isBlank()) continue;
      if (isNumeric(segment)) {
        id = segment;
      } else if (ACTION_SEGMENTS.contains(segment.toLowerCase(Locale.ROOT))) {
        // Keep whatever resource we'd already resolved; the verb isn't the subject.
        continue;
      } else {
        resource = segment;
        id = null;
      }
    }
    return new PathTarget(singularize(resource), id);
  }

  private static boolean isNumeric(String s) {
    for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
    return !s.isEmpty();
  }

  private static String singularize(String resource) {
    if (resource == null || resource.isBlank()) return null;
    String r = resource;
    if (r.endsWith("ies")) r = r.substring(0, r.length() - 3) + "y";
    else if (r.endsWith("sses")) r = r.substring(0, r.length() - 2);
    else if (r.endsWith("s") && !r.endsWith("ss")) r = r.substring(0, r.length() - 1);
    return r.toUpperCase(Locale.ROOT).replace('-', '_');
  }

  private static AuthenticatedUser currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) ? u : null;
  }

  private static String emailFromBody(HttpServletRequest request) {
    if (!(request instanceof ContentCachingRequestWrapper wrapper)) return null;
    byte[] body = wrapper.getContentAsByteArray();
    if (body.length == 0) return null;
    Matcher m = EMAIL_IN_BODY.matcher(new String(body, StandardCharsets.UTF_8));
    return m.find() ? m.group(1) : null;
  }

  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
    return request.getRemoteAddr();
  }

  record PathTarget(String entityType, String entityId) {}
}
