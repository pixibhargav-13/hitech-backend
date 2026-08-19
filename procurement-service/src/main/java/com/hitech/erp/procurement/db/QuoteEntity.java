package com.hitech.erp.procurement.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Everything one vendor came back with, against one enquiry. */
@Getter
@Setter
@Entity
@Table(name = "procurement_quotes")
public class QuoteEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rfq_id", nullable = false)
  private RfqEntity rfq;

  /** A Vyapar party. Procurement keeps no vendor list of its own. */
  @Column(name = "vendor_party_id", nullable = false)
  private Long vendorPartyId;

  /** Bumped when a revised quote replaces this one, so the comparison can say which it shows. */
  @Column(nullable = false)
  private int version = 1;

  @Column(name = "received_on", length = 10)
  private String receivedOn;

  @Column(name = "delivery_days")
  private Integer deliveryDays;

  /** Whole-quote terms, applied after the lines are totalled. */
  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal discount = BigDecimal.ZERO;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal charges = BigDecimal.ZERO;

  @Column(name = "tax_percent", nullable = false, precision = 6, scale = 2)
  private BigDecimal taxPercent = BigDecimal.ZERO;

  @Column(length = 500)
  private String note;

  /**
   * BUYER = we keyed it in, VENDOR = the supplier submitted it through their link. A price the
   * supplier entered themselves is the one that can be pointed at in a disagreement.
   */
  @Column(nullable = false, length = 20)
  private String source = "BUYER";

  /** A submitted quote locks, so it cannot be revised silently once the comparison has been read. */
  @Column(nullable = false)
  private boolean locked = false;

  @Column(name = "submitted_at")
  private java.time.LocalDateTime submittedAt;

  @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<QuoteLineEntity> lines = new ArrayList<>();

  public void addLine(QuoteLineEntity l) {
    l.setQuote(this);
    lines.add(l);
  }
}
