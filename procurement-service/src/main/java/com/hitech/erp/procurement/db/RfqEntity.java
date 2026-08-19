package com.hitech.erp.procurement.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * A request for quotation — the enquiry sent to several suppliers.
 *
 * <p>Procurement owns this and nothing downstream of it: once a line is awarded the order becomes a
 * Vyapar purchase order, and the money is recorded there. Holding the vendor as a Vyapar party id,
 * rather than a name or a second vendor table, is what makes that handover exact.
 */
@Getter
@Setter
@Entity
@Table(name = "procurement_rfqs")
public class RfqEntity extends BaseEntity {

  @Column(name = "rfq_no", nullable = false, length = 40, unique = true)
  private String rfqNo;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(name = "project_id")
  private Long projectId;

  /** Draft, Sent, Responses In, Awarded or Closed — derived on save, see RfqService. */
  @Column(nullable = false, length = 20)
  private String status = "Draft";

  @Column(name = "rfq_date", length = 10)
  private String rfqDate;

  /** The window vendors may reply in. */
  @Column(name = "due_by", length = 10)
  private String dueBy;

  /** ITEM = tax per line, BILL = one rate on the whole bill. */
  @Column(name = "tax_type", nullable = false, length = 10)
  private String taxType = "ITEM";

  @Column(name = "bidding_start_date", length = 10)
  private String biddingStartDate;

  @Column(name = "bidding_end_date", length = 10)
  private String biddingEndDate;

  /** When the whole enquiry is wanted on site; a line may override it. */
  @Column(name = "delivery_date", length = 10)
  private String deliveryDate;

  @Column(columnDefinition = "text")
  private String terms;

  /**
   * Held on the document rather than looked up live: an address printed on an enquiry sent in
   * August must still read the same next year, even if the firm moves.
   */
  @Column(name = "bill_to_name", length = 200)
  private String billToName;

  @Column(name = "bill_to_address", columnDefinition = "text")
  private String billToAddress;

  @Column(name = "bill_to_gstin", length = 20)
  private String billToGstin;

  @Column(name = "ship_to_name", length = 200)
  private String shipToName;

  @Column(name = "ship_to_address", columnDefinition = "text")
  private String shipToAddress;

  @Column(name = "ship_to_gstin", length = 20)
  private String shipToGstin;

  @Column(name = "ship_same_as_bill", nullable = false)
  private boolean shipSameAsBill = false;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(name = "created_by")
  private Long createdBy;

  @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC, id ASC")
  private List<RfqLineEntity> lines = new ArrayList<>();

  @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id ASC")
  private List<QuoteEntity> quotes = new ArrayList<>();

  /** Who was invited — not the same as who replied. */
  @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id ASC")
  private List<RfqSupplierEntity> suppliers = new ArrayList<>();

  public void addLine(RfqLineEntity l) {
    l.setRfq(this);
    lines.add(l);
  }

  public void addQuote(QuoteEntity q) {
    q.setRfq(this);
    quotes.add(q);
  }

  public void addSupplier(RfqSupplierEntity s) {
    s.setRfq(this);
    suppliers.add(s);
  }
}
