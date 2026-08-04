package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * A member's payroll detail — the "how they're paid" record, one per member (unique on userId).
 * Identity (name, phone, email, department, Office/Site) lives on the Member in user-management
 * and is NOT duplicated here; this is purely the money + policy-assignment half.
 */
@Getter
@Setter
@Entity
@Table(name = "payroll_profiles")
public class PayrollProfileEntity extends BaseEntity {

  /** The ERP user (member) this profile belongs to — the join key. One profile per member. */
  @Column(name = "user_id", nullable = false, unique = true)
  private Long userId;

  /** REGULAR, CONTRACTOR or WORK_BASIS. */
  @Column(nullable = false, length = 20)
  private String category = "REGULAR";

  @Column(length = 120)
  private String designation;

  @Column(name = "joining_date")
  private java.time.LocalDate joiningDate;

  // ---- Salary structure ----
  @Column(name = "monthly_ctc", nullable = false, precision = 12, scale = 2)
  private BigDecimal monthlyCtc = BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal basic = BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal hra = BigDecimal.ZERO;

  @Column(name = "other_allowances", nullable = false, precision = 12, scale = 2)
  private BigDecimal otherAllowances = BigDecimal.ZERO;

  /** DAILY, HOURLY, PIECE — only for WORK_BASIS; null for salaried categories. */
  @Column(name = "work_type", length = 10)
  private String workType;

  @Column(name = "work_rate", nullable = false, precision = 12, scale = 2)
  private BigDecimal workRate = BigDecimal.ZERO;

  @Column(nullable = false)
  private boolean pf = false;

  @Column(nullable = false)
  private boolean esic = false;

  @Column(nullable = false)
  private boolean pt = false;

  // ---- Bank & identity ----
  @Column(name = "bank_account", length = 40)
  private String bankAccount;

  @Column(length = 20)
  private String ifsc;

  @Column(name = "bank_name", length = 120)
  private String bankName;

  @Column(length = 15)
  private String pan;

  /** Identity documents as a JSON array of {type, number} — Aadhaar, PAN and any user-added extras. */
  @Column(columnDefinition = "text")
  private String documents;

  /** Salary components (earnings + deductions) as delimited text — see the frontend salaryComponents. */
  @Column(columnDefinition = "text")
  private String components;

  // ---- Assigned pre-setup policies (from Payroll -> Setup); null = none assigned ----
  @Column(name = "shift_id")
  private Long shiftId;

  @Column(name = "holiday_policy_id")
  private Long holidayPolicyId;

  @Column(name = "leave_policy_id")
  private Long leavePolicyId;
}
