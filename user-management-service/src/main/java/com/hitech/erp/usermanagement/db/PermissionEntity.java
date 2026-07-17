package com.hitech.erp.usermanagement.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "permissions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"module_id", "action"}))
public class PermissionEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "module_id", nullable = false)
  private ModuleEntity module;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PermissionAction action;

  /** Effective permission code, e.g. "PROJECT:VIEW" - used as the JWT authority string. */
  public String getCode() {
    return module.getCode() + ":" + action.name();
  }
}
