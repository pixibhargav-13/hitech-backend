package com.hitech.erp.audit.service;

import com.hitech.erp.audit.db.AuditAction;
import com.hitech.erp.audit.db.AuditLogEntity;
import com.hitech.erp.audit.db.AuditLogRepository;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit rows. Recording runs in its own transaction so a failed audit write can never
 * roll back the business operation that triggered it (and vice versa).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRecorder {

  private final AuditLogRepository repository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditEvent event) {
    try {
      AuditLogEntity log = new AuditLogEntity();
      AuthenticatedUser actor = event.actor();
      if (actor != null) {
        log.setActorUserId(actor.id());
        log.setActorEmail(actor.email());
        log.setActorName(actor.fullName());
        log.setActorRole(actor.roleName());
      } else {
        log.setActorEmail(event.fallbackActorEmail());
        log.setActorName(event.fallbackActorEmail());
      }
      log.setAction(event.action());
      log.setEntityType(event.entityType());
      log.setEntityId(event.entityId());
      log.setSummary(trim(event.summary(), 500));
      log.setHttpMethod(event.httpMethod());
      log.setPath(trim(event.path(), 255));
      log.setStatusCode(event.statusCode());
      log.setIpAddress(trim(event.ipAddress(), 60));
      log.setUserAgent(trim(event.userAgent(), 255));
      repository.save(log);
    } catch (Exception ex) {
      // Auditing must never break the request it is observing.
      log.warn("Failed to write audit log for {} {}: {}", event.httpMethod(), event.path(), ex.getMessage());
    }
  }

  private static String trim(String value, int max) {
    if (value == null) return null;
    return value.length() <= max ? value : value.substring(0, max);
  }

  /** Everything needed to describe one auditable action. */
  public record AuditEvent(
      AuthenticatedUser actor,
      String fallbackActorEmail,
      AuditAction action,
      String entityType,
      String entityId,
      String summary,
      String httpMethod,
      String path,
      Integer statusCode,
      String ipAddress,
      String userAgent) {}
}
