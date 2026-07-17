package com.hitech.erp.audit.api;

import com.hitech.erp.api.audit.AuditApi;
import com.hitech.erp.api.audit.model.AuditFilterOptions;
import com.hitech.erp.api.audit.model.AuditLogPageResponse;
import com.hitech.erp.audit.service.AuditQueryService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** Read-only. The trail is written by the audit filter, never through the API. */
@RestController
@RequiredArgsConstructor
public class AuditController implements AuditApi {

  private final AuditQueryService auditQueryService;

  @Override
  @PreAuthorize("hasAuthority('AUDIT:VIEW')")
  public ResponseEntity<AuditLogPageResponse> getAuditLogs(
      Optional<Integer> page,
      Optional<Integer> size,
      Optional<Long> actorUserId,
      Optional<String> action,
      Optional<String> entityType,
      Optional<String> from,
      Optional<String> to,
      Optional<String> q) {
    return ResponseEntity.ok(
        auditQueryService.getLogs(
            page.orElse(0),
            size.orElse(25),
            actorUserId.orElse(null),
            action.orElse(null),
            entityType.orElse(null),
            from.orElse(null),
            to.orElse(null),
            q.orElse(null)));
  }

  @Override
  @PreAuthorize("hasAuthority('AUDIT:VIEW')")
  public ResponseEntity<AuditFilterOptions> getAuditFilters() {
    return ResponseEntity.ok(auditQueryService.getFilterOptions());
  }
}
