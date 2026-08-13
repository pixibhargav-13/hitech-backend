package com.hitech.erp.vyapar.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * How much of one payment has been applied to one invoice — Vyapar's "Link Payment to Txns".
 *
 * <p>A payment is not owned by a single invoice: a lump-sum receipt gets spread across several
 * open bills, and whatever is left over shows in the party ledger as "Unused". Plain id columns
 * rather than JPA relations, because links are always read and written in bulk for one payment.
 */
@Getter
@Setter
@Entity
@Table(name = "vyapar_payment_links")
public class PaymentLinkEntity extends BaseEntity {

  @Column(name = "payment_id", nullable = false)
  private Long paymentId;

  @Column(name = "invoice_id", nullable = false)
  private Long invoiceId;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal amount = BigDecimal.ZERO;
}
