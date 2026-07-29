package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** One monthly payroll cycle — every on-payroll member gets a payslip inside it. */
@Getter
@Setter
@Entity
@Table(name = "payroll_runs")
public class PayrollRunEntity extends BaseEntity {

  /** yyyy-MM, unique. */
  @Column(nullable = false, length = 7, unique = true)
  private String month;

  /** DRAFT (regeneratable) → LOCKED (reviewed, no more edits) → PAID (payments recorded). */
  @Column(nullable = false, length = 15)
  private String status = "DRAFT";

  @Column(name = "total_net", nullable = false, precision = 14, scale = 2)
  private BigDecimal totalNet = BigDecimal.ZERO;

  @Column(name = "total_gross", nullable = false, precision = 14, scale = 2)
  private BigDecimal totalGross = BigDecimal.ZERO;

  @Column(name = "person_count", nullable = false)
  private int personCount = 0;

  @Column(name = "locked_by")
  private Long lockedBy;

  @Column(name = "locked_at")
  private LocalDateTime lockedAt;

  @Column(name = "paid_by")
  private Long paidBy;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<PayslipEntity> payslips = new ArrayList<>();
}
