package com.hitech.erp.usermanagement.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "app_users")
public class AppUserEntity extends BaseEntity {

  /**
   * Sign-in address. Null for members who don't log in — site labour is on the payroll and needs
   * attendance, leave and a payslip, but has no reason to ever open the app. Forcing an address on
   * those records only produced fake ones.
   */
  @Column(unique = true, length = 255)
  private String email;

  /** Null for non-login members. Required (and validated) whenever {@link #loginUser} is true. */
  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  /**
   * Whether this member can sign in. When false the record is a payroll/directory entry only, and
   * the credential fields are neither required nor shown.
   */
  @Column(name = "is_login_user", nullable = false)
  private boolean loginUser = true;

  @Column(name = "full_name", nullable = false, length = 255)
  private String fullName;

  @Column(name = "phone_number", length = 20)
  private String phoneNumber;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  private RoleEntity role;

  /** Org grouping (Civil, Electrical…). Optional — null until someone assigns one. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id")
  private DepartmentEntity department;

  /** OFFICE (not project-based) or SITE (works on project sites). Null until classified. */
  @Column(name = "staff_type", length = 10)
  private String staffType;

  /** Whether this member is on payroll — can punch and has a payroll profile. */
  @Column(name = "on_payroll", nullable = false)
  private boolean onPayroll;

  /** Profile photo, stored inline as a data URL (small, downscaled client-side). Null until set. */
  @Column(name = "photo_url", columnDefinition = "text")
  private String photoUrl;
}
