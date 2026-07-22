package com.hitech.erp.usermanagement.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An org grouping (Civil, Electrical, Accounts…). Distinct from {@link RoleEntity}, which controls
 * permissions — a department says which team someone belongs to, not what they may do.
 */
@Getter
@Setter
@Entity
@Table(name = "departments")
public class DepartmentEntity extends BaseEntity {

  @Column(nullable = false, length = 120, unique = true)
  private String name;

  @Column(length = 30)
  private String code;

  @Column(length = 400)
  private String description;

  /** Optional department head (an app_users id). */
  @Column(name = "head_user_id")
  private Long headUserId;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;
}
