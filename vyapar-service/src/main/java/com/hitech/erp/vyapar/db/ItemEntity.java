package com.hitech.erp.vyapar.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** A stock item or service that can be billed. */
@Getter
@Setter
@Entity
@Table(name = "vyapar_items")
public class ItemEntity extends BaseEntity {

  /** The bank/cash account this item belongs to (null = all accounts). */
  @Column(name = "bank_account_id")
  private Long bankAccountId;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(length = 120)
  private String category;

  @Column(length = 500)
  private String description;

  @Column(name = "item_code", length = 60)
  private String itemCode;

  @Column(length = 30)
  private String hsn;

  @Column(nullable = false, length = 30)
  private String unit = "PCS";

  @Column(name = "sale_price", nullable = false, precision = 16, scale = 2)
  private BigDecimal salePrice = BigDecimal.ZERO;

  @Column(name = "purchase_price", nullable = false, precision = 16, scale = 2)
  private BigDecimal purchasePrice = BigDecimal.ZERO;

  @Column(name = "tax_percent", nullable = false, precision = 6, scale = 2)
  private BigDecimal taxPercent = BigDecimal.ZERO;

  @Column(name = "stock_qty", nullable = false, precision = 16, scale = 3)
  private BigDecimal stockQty = BigDecimal.ZERO;

  @Column(name = "low_stock_alert", nullable = false, precision = 16, scale = 3)
  private BigDecimal lowStockAlert = BigDecimal.ZERO;

  /** Services carry no stock. */
  @Column(name = "is_service", nullable = false)
  private boolean service = false;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;
}
