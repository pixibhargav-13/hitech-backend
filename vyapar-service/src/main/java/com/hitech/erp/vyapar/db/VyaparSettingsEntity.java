package com.hitech.erp.vyapar.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The module's preferences — Vyapar's Settings screen, as one row (id = 1).
 *
 * <p>These are not cosmetic: the frontend reads them to decide how many decimals to render, how to
 * round a total, whether Due Date exists on the form, and which document types appear at all.
 * Anything hardcoded in the UI that Vyapar exposes as a switch belongs here.
 */
@Getter
@Setter
@Entity
@Table(name = "vyapar_settings")
public class VyaparSettingsEntity extends BaseEntity {

  /** Vyapar's "Amount (upto Decimal Places)" — this client runs 3. */
  @Column(name = "amount_decimals", nullable = false)
  private int amountDecimals = 3;

  @Column(name = "quantity_decimals", nullable = false)
  private int quantityDecimals = 3;

  @Column(name = "round_off_enabled", nullable = false)
  private boolean roundOffEnabled = true;

  /** NEAREST, UP or DOWN. */
  @Column(name = "round_off_mode", nullable = false, length = 20)
  private String roundOffMode = "NEAREST";

  /** Round to the nearest 1, 10 or 100. */
  @Column(name = "round_off_to", nullable = false)
  private int roundOffTo = 1;

  @Column(name = "due_dates_enabled", nullable = false)
  private boolean dueDatesEnabled = false;

  @Column(name = "link_payments_enabled", nullable = false)
  private boolean linkPaymentsEnabled = true;

  @Column(name = "item_wise_tax", nullable = false)
  private boolean itemWiseTax = true;

  @Column(name = "item_wise_discount", nullable = false)
  private boolean itemWiseDiscount = true;

  @Column(name = "display_purchase_price", nullable = false)
  private boolean displayPurchasePrice = true;

  @Column(name = "transaction_wise_tax", nullable = false)
  private boolean transactionWiseTax = false;

  @Column(name = "transaction_wise_disc", nullable = false)
  private boolean transactionWiseDiscount = true;

  @Column(name = "estimate_enabled", nullable = false)
  private boolean estimateEnabled = true;

  @Column(name = "proforma_enabled", nullable = false)
  private boolean proformaEnabled = true;

  @Column(name = "orders_enabled", nullable = false)
  private boolean ordersEnabled = true;

  @Column(name = "delivery_challan_enabled", nullable = false)
  private boolean deliveryChallanEnabled = true;

  /** Per-document-type prefixes as JSON, e.g. {@code {"SALE":"GJ/RA/26-27/"}}. */
  @Column(columnDefinition = "TEXT")
  private String prefixes;
}
