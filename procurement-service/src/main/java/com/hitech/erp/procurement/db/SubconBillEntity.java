package com.hitech.erp.procurement.db;

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

/**
 * A running bill against a subcontract.
 *
 * <p>A subcontract is not settled once. A seven-lakh order is paid across a dozen bills over
 * months, which is why "what is still owed on this order" is a question only these rows can answer.
 *
 * <p>Retention and material recovery sit on the bill rather than in a note because they are
 * deductions on its face: money held back, and the value of cement and steel we issued him. Both
 * come off what he is actually paid.
 */
@Getter
@Setter
@Entity
@Table(name = "procurement_subcon_bills")
public class SubconBillEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "work_order_id", nullable = false)
  private WorkOrderEntity workOrder;

  @Column(name = "bill_no", length = 40)
  private String billNo;

  @Column(name = "bill_date", length = 10)
  private String billDate;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal amount = BigDecimal.ZERO;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal retention = BigDecimal.ZERO;

  @Column(name = "material_recovery", nullable = false, precision = 16, scale = 2)
  private BigDecimal materialRecovery = BigDecimal.ZERO;

  @Column(length = 500)
  private String note;

  /** The Vyapar purchase bill this became, once booked. Null while it is only recorded here. */
  @Column(name = "vyapar_invoice_id")
  private Long vyaparInvoiceId;

  @Column(name = "created_by")
  private Long createdBy;
}
