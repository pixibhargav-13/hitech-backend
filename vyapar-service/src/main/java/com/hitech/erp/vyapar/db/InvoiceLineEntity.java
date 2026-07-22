package com.hitech.erp.vyapar.db;

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

@Getter
@Setter
@Entity
@Table(name = "vyapar_invoice_lines")
public class InvoiceLineEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false)
  private InvoiceEntity invoice;

  /** Optional link back to the catalogue item (free-text lines are allowed). */
  @Column(name = "item_id")
  private Long itemId;

  @Column(name = "item_name", nullable = false, length = 200)
  private String itemName;

  @Column(nullable = false, precision = 16, scale = 3)
  private BigDecimal quantity = BigDecimal.ONE;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal rate = BigDecimal.ZERO;

  @Column(length = 500)
  private String description;

  @Column(length = 30)
  private String unit;

  /** Line discount, enterable as a percent or a flat amount — both are kept. */
  @Column(name = "discount_percent", nullable = false, precision = 6, scale = 2)
  private BigDecimal discountPercent = BigDecimal.ZERO;

  @Column(name = "discount_amount", nullable = false, precision = 16, scale = 2)
  private BigDecimal discountAmount = BigDecimal.ZERO;

  @Column(name = "tax_percent", nullable = false, precision = 6, scale = 2)
  private BigDecimal taxPercent = BigDecimal.ZERO;

  @Column(name = "tax_amount", nullable = false, precision = 16, scale = 2)
  private BigDecimal taxAmount = BigDecimal.ZERO;

  /** Line total including its own tax. */
  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 0;
}
