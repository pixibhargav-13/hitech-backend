package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** A reimbursement claim — expense a member paid for, awaiting approval + payout. */
@Getter
@Setter
@Entity
@Table(name = "payroll_reimbursements")
public class ReimbursementEntity extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "expense_type", nullable = false, length = 120)
  private String expenseType;

  @Column(name = "claim_id", nullable = false, length = 30)
  private String claimId;

  @Column(name = "expense_date", nullable = false)
  private LocalDate expenseDate;

  @Column(name = "applied_at", nullable = false)
  private LocalDate appliedAt;

  @Column(name = "approved_at")
  private LocalDate approvedAt;

  @Column(name = "settlement_date")
  private LocalDate settlementDate;

  @Column(name = "requested_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal requestedAmount;

  @Column(name = "approved_amount", precision = 12, scale = 2)
  private BigDecimal approvedAmount;

  @Column(name = "approver_id")
  private Long approverId;

  /** PENDING, APPROVED, REJECTED, PAID. */
  @Column(nullable = false, length = 15)
  private String status = "PENDING";
}
