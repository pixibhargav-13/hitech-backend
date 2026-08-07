package com.hitech.erp.tender.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A material supplier / manufacturer party referenced while preparing a bid. */
@Getter
@Setter
@Entity
@Table(name = "tender_materials")
public class TenderMaterialEntity extends BaseEntity {

  @Column(length = 300)
  private String party;

  @Column(name = "manufacturer_type", length = 200)
  private String manufacturerType;

  @Column(length = 200)
  private String make;

  @Column(length = 300)
  private String location;

  @Column(length = 120)
  private String contact;

  @Column(length = 200)
  private String email;
}
