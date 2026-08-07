package com.hitech.erp.tender.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Hardcopy dispatch / courier tracking record for a tender's physical documents. */
@Getter
@Setter
@Entity
@Table(name = "tender_hardcopy")
public class TenderHardcopyEntity extends BaseEntity {

  @Column(length = 30)
  private String date;

  @Column(name = "name_of_work", length = 2000)
  private String nameOfWork;

  @Column(name = "tender_ref", length = 120)
  private String tenderRef;

  @Column(name = "document_list", length = 2000)
  private String documentList;

  @Column(name = "packed_by", length = 200)
  private String packedBy;

  @Column(name = "dispatch_by", length = 200)
  private String dispatchBy;

  @Column(name = "tracking_no", length = 120)
  private String trackingNo;

  @Column(length = 60)
  private String arrived;

  @Column(name = "arrived_date", length = 30)
  private String arrivedDate;

  @Column(length = 1000)
  private String remarks;
}
