package com.hitech.erp.vyapar.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Every billing document shares this shape — sale invoice, purchase bill, estimate, order and
 * delivery challan differ only by {@code docType}, exactly like Vyapar itself.
 */
@Getter
@Setter
@Entity
@Table(name = "vyapar_invoices")
public class InvoiceEntity extends BaseEntity {

  /** The bank/cash account this document belongs to (null = all accounts). */
  @Column(name = "bank_account_id")
  private Long bankAccountId;

  /** SALE, PURCHASE, ESTIMATE, SALE_ORDER, DELIVERY_CHALLAN, SALE_RETURN, PURCHASE_RETURN. */
  @Column(name = "doc_type", nullable = false, length = 30)
  private String docType = "SALE";

  @Column(name = "invoice_no", nullable = false, length = 60)
  private String invoiceNo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "party_id")
  private PartyEntity party;

  @Column(name = "invoice_date", length = 30)
  private String invoiceDate;

  @Column(name = "due_date", length = 30)
  private String dueDate;

  @Column(name = "sub_total", nullable = false, precision = 16, scale = 2)
  private BigDecimal subTotal = BigDecimal.ZERO;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal discount = BigDecimal.ZERO;

  @Column(name = "tax_amount", nullable = false, precision = 16, scale = 2)
  private BigDecimal taxAmount = BigDecimal.ZERO;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal total = BigDecimal.ZERO;

  /** How much of the total has been settled; balance = total - paidAmount. */
  @Column(name = "paid_amount", nullable = false, precision = 16, scale = 2)
  private BigDecimal paidAmount = BigDecimal.ZERO;

  @Column(name = "payment_type", nullable = false, length = 40)
  private String paymentType = "Cash";

  /** Vyapar's Credit/Cash toggle — cash settles the document immediately. */
  @Column(name = "is_cash", nullable = false)
  private boolean cash = true;

  /** GST place of supply, which decides IGST vs CGST/SGST. */
  @Column(name = "state_of_supply", length = 120)
  private String stateOfSupply;

  @Column(name = "invoice_prefix", length = 40)
  private String invoicePrefix;

  @Column(length = 1000)
  private String terms;

  /** Whole-document discount as a percent of sub-total; the flat value lives in `discount`. */
  @Column(name = "discount_percent", nullable = false, precision = 6, scale = 2)
  private BigDecimal discountPercent = BigDecimal.ZERO;

  @Column(name = "round_off", nullable = false, precision = 16, scale = 2)
  private BigDecimal roundOff = BigDecimal.ZERO;

  @Column(length = 1000)
  private String notes;

  @Column(name = "created_by")
  private Long createdBy;

  @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC, id ASC")
  private List<InvoiceLineEntity> lines = new ArrayList<>();

  public void addLine(InvoiceLineEntity line) {
    line.setInvoice(this);
    lines.add(line);
  }
}
