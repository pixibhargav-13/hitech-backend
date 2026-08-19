package com.hitech.erp.procurement.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * A supplier the enquiry was sent to.
 *
 * <p>Separate from {@link QuoteEntity} on purpose: invited is not the same as replied. Without this
 * row there is no way to say "5 invited, 2 responded", chase the silent three, or resend to one of
 * them — the enquiry would only ever know about suppliers who happened to answer.
 */
@Getter
@Setter
@Entity
@Table(name = "procurement_rfq_suppliers")
public class RfqSupplierEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rfq_id", nullable = false)
  private RfqEntity rfq;

  /** A Vyapar party; name, phone and email are read from there rather than copied. */
  @Column(name = "vendor_party_id", nullable = false)
  private Long vendorPartyId;

  /** Stamped when the enquiry is actually sent, so "added but not yet sent" stays visible. */
  @Column(name = "sent_at")
  private LocalDateTime sentAt;

  /**
   * The secret in this supplier's quote link. One per supplier rather than one per enquiry: the
   * link identifies who is quoting, so nobody can read a rival's prices, and a leaked link exposes
   * one supplier's own quote rather than the whole comparison.
   */
  @Column(name = "share_token", length = 64)
  private String shareToken;

  /** When they last opened it — "never looked" needs a resend, "looked and didn't quote" a call. */
  @Column(name = "opened_at")
  private LocalDateTime openedAt;
}
