package com.hitech.erp.vyapar.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** A manual stock correction ("Adjust Item") — kept as its own audit row for the item ledger. */
@Getter
@Setter
@Entity
@Table(name = "vyapar_stock_adjustments")
public class StockAdjustmentEntity extends BaseEntity {

  @Column(name = "item_id", nullable = false)
  private Long itemId;

  @Column(name = "bank_account_id")
  private Long bankAccountId;

  /** ADD or REDUCE. */
  @Column(nullable = false, length = 10)
  private String mode = "ADD";

  @Column(nullable = false, precision = 16, scale = 3)
  private BigDecimal quantity = BigDecimal.ZERO;

  @Column(name = "at_price", nullable = false, precision = 16, scale = 2)
  private BigDecimal atPrice = BigDecimal.ZERO;

  @Column(name = "adj_date", length = 30)
  private String adjDate;

  @Column(length = 500)
  private String note;
}
