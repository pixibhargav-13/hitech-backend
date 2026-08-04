package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * One member's payslip inside a run. Formula: gross (from attendance × salary) − PF − ESIC − PT
 * − loan EMI + reimbursements = net. Unique on (run, user) — a member has at most one payslip
 * per month.
 */
@Getter
@Setter
@Entity
@Table(
    name = "payroll_payslips",
    uniqueConstraints = @UniqueConstraint(name = "uq_payslip_run_user", columnNames = {"run_id", "user_id"}))
public class PayslipEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "run_id", nullable = false)
  private PayrollRunEntity run;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal gross = BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal pf = BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal esic = BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal pt = BigDecimal.ZERO;

  /** Sum of deduction components that aren't PF/ESIC/PT (custom deductions). */
  @Column(name = "other_deductions", nullable = false, precision = 12, scale = 2)
  private BigDecimal otherDeductions = BigDecimal.ZERO;

  /** Human-readable deduction breakdown as delimited text "name|amount;…" for the payslip. */
  @Column(name = "deductions_detail", columnDefinition = "text")
  private String deductionsDetail;

  @Column(name = "loan_emi", nullable = false, precision = 12, scale = 2)
  private BigDecimal loanEmi = BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal reimbursements = BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal net = BigDecimal.ZERO;

  @Column(name = "payable_days", nullable = false, precision = 4, scale = 1)
  private BigDecimal payableDays = BigDecimal.ZERO;

  @Column(name = "total_days", nullable = false)
  private int totalDays = 0;
}
