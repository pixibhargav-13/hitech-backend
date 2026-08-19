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
 * Material issued to a subcontractor against his order, and anything he sends back.
 *
 * <p>Held as movements rather than a running balance, so "he still holds 40 bags" is arrived at
 * from rows that each carry a date and a person — which is what an argument about recovery needs.
 */
@Getter
@Setter
@Entity
@Table(name = "procurement_subcon_materials")
public class SubconMaterialEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "work_order_id", nullable = false)
  private WorkOrderEntity workOrder;

  @Column(name = "item_id")
  private Long itemId;

  @Column(name = "item_name", nullable = false, length = 200)
  private String itemName;

  @Column(length = 30)
  private String unit;

  /** ISSUE = out to him, RETURN = back to us, CONSUMED = used up on the work. */
  @Column(nullable = false, length = 12)
  private String movement = "ISSUE";

  @Column(nullable = false, precision = 16, scale = 3)
  private BigDecimal quantity = BigDecimal.ZERO;

  /** What it is recovered at. Zero means free issue — common, and not the same as unpriced. */
  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal rate = BigDecimal.ZERO;

  @Column(name = "moved_on", length = 10)
  private String movedOn;

  @Column(length = 300)
  private String note;

  @Column(name = "created_by")
  private Long createdBy;
}
