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
 * One priced line of a subcontract.
 *
 * <p>The four dimension fields are the point. A site clerk measures 4 x 12.5 x 0.6 x 0.15 and the
 * quantity falls out of it; keeping the four numbers rather than only their product is what lets a
 * disputed bill be re-checked months later against what was actually measured on the ground. They
 * are all optional, because plenty of site work is quoted per running metre with no dimensions at
 * all, and a forced 1 in every box would read as a measurement that nobody took.
 */
@Getter
@Setter
@Entity
@Table(name = "procurement_work_order_items")
public class WorkOrderItemEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "work_order_id", nullable = false)
  private WorkOrderEntity workOrder;

  @Column(name = "item_id")
  private Long itemId;

  @Column(name = "item_name", nullable = false, length = 200)
  private String itemName;

  @Column(length = 300)
  private String description;

  @Column(length = 30)
  private String unit;

  @Column(name = "dim_n", precision = 16, scale = 3)
  private BigDecimal dimN;

  @Column(name = "dim_l", precision = 16, scale = 3)
  private BigDecimal dimL;

  @Column(name = "dim_w", precision = 16, scale = 3)
  private BigDecimal dimW;

  @Column(name = "dim_h", precision = 16, scale = 3)
  private BigDecimal dimH;

  @Column(nullable = false, precision = 16, scale = 3)
  private BigDecimal quantity = BigDecimal.ONE;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal rate = BigDecimal.ZERO;

  /** Physical progress on this line. Per line, because a subcontract is routinely 90% laid and 0% tested. */
  @Column(name = "progress_percent", nullable = false, precision = 6, scale = 2)
  private BigDecimal progressPercent = BigDecimal.ZERO;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;
}
