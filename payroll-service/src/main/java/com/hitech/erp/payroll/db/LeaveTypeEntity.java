package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One leave type within a {@link LeavePolicyEntity}, e.g. "Casual Leave: 12 days, monthly accrual". */
@Getter
@Setter
@Entity
@Table(name = "payroll_leave_types")
public class LeaveTypeEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "policy_id", nullable = false)
  private LeavePolicyEntity policy;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "annual_count", nullable = false)
  private int annualCount;

  /** ALL_AT_ONCE or MONTHLY. */
  @Column(nullable = false, length = 20)
  private String accrual = "ALL_AT_ONCE";

  @Column(nullable = false)
  private boolean paid = true;
}
