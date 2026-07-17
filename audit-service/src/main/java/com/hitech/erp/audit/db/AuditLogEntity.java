package com.hitech.erp.audit.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One immutable row per state-changing action. Never updated or deleted by the app. */
@Getter
@Setter
@Entity
@Table(name = "audit_logs")
public class AuditLogEntity extends BaseEntity {

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(name = "actor_email", length = 255)
  private String actorEmail;

  @Column(name = "actor_name", length = 255)
  private String actorName;

  @Column(name = "actor_role", length = 100)
  private String actorRole;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AuditAction action;

  @Column(name = "entity_type", length = 60)
  private String entityType;

  @Column(name = "entity_id", length = 60)
  private String entityId;

  @Column(length = 500)
  private String summary;

  @Column(name = "http_method", length = 10)
  private String httpMethod;

  @Column(length = 255)
  private String path;

  @Column(name = "status_code")
  private Integer statusCode;

  @Column(name = "ip_address", length = 60)
  private String ipAddress;

  @Column(name = "user_agent", length = 255)
  private String userAgent;
}
