package com.hitech.erp.vyapar.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Money received from a customer (IN) or paid to a supplier (OUT). */
@Getter
@Setter
@Entity
@Table(name = "vyapar_payments")
public class PaymentEntity extends BaseEntity {

  /** The bank/cash account this payment belongs to (null = all accounts). */
  @Column(name = "bank_account_id")
  private Long bankAccountId;

  /** IN or OUT. */
  @Column(nullable = false, length = 10)
  private String direction = "IN";

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "party_id")
  private PartyEntity party;

  /** Optionally settles a specific invoice. */
  @Column(name = "invoice_id")
  private Long invoiceId;

  @Column(name = "payment_date", length = 30)
  private String paymentDate;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal amount = BigDecimal.ZERO;

  @Column(nullable = false, length = 40)
  private String mode = "Cash";

  @Column(length = 120)
  private String reference;

  @Column(length = 500)
  private String notes;
}
