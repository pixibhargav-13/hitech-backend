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

/** One thing being asked for, and — once decided — who is supplying it. */
@Getter
@Setter
@Entity
@Table(name = "procurement_rfq_lines")
public class RfqLineEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rfq_id", nullable = false)
  private RfqEntity rfq;

  /** Set when the buyer picked from the Vyapar catalogue rather than typing a name. */
  @Column(name = "item_id")
  private Long itemId;

  @Column(name = "item_name", nullable = false, length = 200)
  private String itemName;

  @Column(length = 30)
  private String unit;

  /** Suppliers need it to quote tax correctly. */
  @Column(name = "hsn_code", length = 20)
  private String hsnCode;

  /** The brand/spec sub-line under the item name, e.g. "Kirloskar, IVI, GM". */
  @Column(length = 500)
  private String specification;

  /** Overrides the document's delivery date when one line is wanted earlier than the rest. */
  @Column(name = "delivery_date", length = 10)
  private String deliveryDate;

  @Column(nullable = false, precision = 16, scale = 3)
  private BigDecimal quantity = BigDecimal.ONE;

  /** From a vendor rate card where one exists. Null means there is nothing to judge against. */
  @Column(name = "budget_rate", precision = 16, scale = 2)
  private BigDecimal budgetRate;

  /**
   * The award, per line. A five-line enquiry commonly splits across three suppliers, so the winner
   * belongs here rather than on the RFQ.
   */
  @Column(name = "awarded_vendor_party_id")
  private Long awardedVendorPartyId;

  @Column(name = "award_reason", length = 300)
  private String awardReason;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 0;
}
