package com.hitech.erp.procurement.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * A subcontract: work bought from a labour contractor, priced by measurement.
 *
 * <p>The same shape as an awarded enquiry — a party, priced lines, a total that ends in a Vyapar
 * bill — but billed in instalments over months rather than settled once, and carrying material we
 * issued to him that comes back off what he is paid.
 */
@Getter
@Setter
@Entity
@Table(name = "procurement_work_orders")
public class WorkOrderEntity extends BaseEntity {

  @Column(name = "wo_no", nullable = false, length = 40)
  private String woNo;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(name = "project_id")
  private Long projectId;

  /** A Vyapar party. Procurement keeps no separate contractor list. */
  @Column(name = "vendor_party_id", nullable = false)
  private Long vendorPartyId;

  @Column(nullable = false, length = 20)
  private String status = "Draft";

  @Column(name = "wo_date", length = 10)
  private String woDate;

  @Column(name = "start_date", length = 10)
  private String startDate;

  @Column(name = "end_date", length = 10)
  private String endDate;

  @Column(name = "tax_percent", nullable = false, precision = 6, scale = 2)
  private BigDecimal taxPercent = BigDecimal.ZERO;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal discount = BigDecimal.ZERO;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal charges = BigDecimal.ZERO;

  @Column(name = "bank_account_name", length = 120)
  private String bankAccountName;

  @Column(name = "bank_account_number", length = 40)
  private String bankAccountNumber;

  @Column(name = "bank_ifsc", length = 20)
  private String bankIfsc;

  @Column(columnDefinition = "text")
  private String terms;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(name = "created_by")
  private Long createdBy;

  @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<WorkOrderItemEntity> items = new ArrayList<>();

  @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<SubconBillEntity> bills = new ArrayList<>();

  @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<SubconMaterialEntity> materials = new ArrayList<>();

  public void addItem(WorkOrderItemEntity i) {
    i.setWorkOrder(this);
    items.add(i);
  }

  public void addBill(SubconBillEntity b) {
    b.setWorkOrder(this);
    bills.add(b);
  }

  public void addMaterial(SubconMaterialEntity m) {
    m.setWorkOrder(this);
    materials.add(m);
  }
}
