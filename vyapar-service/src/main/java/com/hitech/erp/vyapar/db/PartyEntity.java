package com.hitech.erp.vyapar.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** A customer or supplier in the Vyapar books. */
@Getter
@Setter
@Entity
@Table(name = "vyapar_parties")
public class PartyEntity extends BaseEntity {

  /** The bank/cash account this party's book belongs to (null = all accounts). */
  @Column(name = "bank_account_id")
  private Long bankAccountId;

  @Column(nullable = false, length = 200)
  private String name;

  /** CUSTOMER or SUPPLIER. */
  @Column(name = "party_type", nullable = false, length = 20)
  private String partyType = "CUSTOMER";

  @Column(length = 30)
  private String phone;

  @Column(length = 200)
  private String email;

  @Column(length = 20)
  private String gstin;

  @Column(name = "billing_address", length = 500)
  private String billingAddress;

  @Column(length = 120)
  private String city;

  /** Positive = they owe us, negative = we owe them. */
  @Column(name = "gst_type", length = 60)
  private String gstType;

  @Column(length = 120)
  private String state;

  @Column(name = "shipping_address", length = 500)
  private String shippingAddress;

  /** Optional segment (Wholesale, Retail…) used by Party Grouping. */
  @Column(name = "party_group", length = 120)
  private String partyGroup;

  @Column(name = "opening_balance", nullable = false, precision = 16, scale = 2)
  private BigDecimal openingBalance = BigDecimal.ZERO;

  /** Date the opening balance is stated as of. */
  @Column(name = "opening_date", length = 30)
  private String openingDate;

  /** null = no credit limit. */
  @Column(name = "credit_limit", precision = 16, scale = 2)
  private BigDecimal creditLimit;

  // Four configurable extras, matching Vyapar's Additional Fields tab.
  @Column(length = 255) private String field1;
  @Column(length = 255) private String field2;
  @Column(length = 255) private String field3;
  @Column(length = 255) private String field4;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;
}
