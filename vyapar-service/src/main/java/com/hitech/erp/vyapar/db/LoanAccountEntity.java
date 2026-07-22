package com.hitech.erp.vyapar.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** A business loan: what was borrowed, what is still owed, and the EMI servicing it. */
@Getter
@Setter
@Entity
@Table(name = "vyapar_loan_accounts")
public class LoanAccountEntity extends BaseEntity {

  @Column(name = "owner_user_id", nullable = false)
  private Long ownerUserId;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 160)
  private String lender;

  @Column(name = "account_number", length = 60)
  private String accountNumber;

  @Column(name = "loan_amount", nullable = false, precision = 16, scale = 2)
  private BigDecimal loanAmount = BigDecimal.ZERO;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal balance = BigDecimal.ZERO;

  @Column(name = "interest_rate", nullable = false, precision = 8, scale = 3)
  private BigDecimal interestRate = BigDecimal.ZERO;

  @Column(name = "term_months", nullable = false)
  private int termMonths = 0;

  @Column(name = "start_date", length = 30)
  private String startDate;

  @Column(name = "emi_amount", nullable = false, precision = 16, scale = 2)
  private BigDecimal emiAmount = BigDecimal.ZERO;
}
