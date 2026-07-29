package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** A salary advance / loan given to a member, repaid as EMIs deducted from monthly payroll. */
@Getter
@Setter
@Entity
@Table(name = "payroll_loans")
public class LoanEntity extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal principal;

  @Column(name = "tenure_months", nullable = false)
  private int tenureMonths;

  @Column(name = "annual_rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal annualRate = BigDecimal.ZERO;

  /** FLAT, SIMPLE, COMPOUND. */
  @Column(name = "interest_type", nullable = false, length = 15)
  private String interestType = "FLAT";

  @Column(name = "disbursement_date", nullable = false)
  private LocalDate disbursementDate;

  /** yyyy-MM — the first month EMI is deducted. */
  @Column(name = "start_month", nullable = false, length = 7)
  private String startMonth;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal emi;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal outstanding;
}
