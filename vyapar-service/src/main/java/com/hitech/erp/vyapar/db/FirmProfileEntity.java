package com.hitech.erp.vyapar.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The letterhead: business name, address, GSTIN and logo stamped onto every PDF and print-out.
 * One row per user, fetched only by the profile endpoint so the logo never rides along on a
 * list query.
 */
@Getter
@Setter
@Entity
@Table(name = "vyapar_firm_profile")
public class FirmProfileEntity extends BaseEntity {

  @Column(name = "owner_user_id", nullable = false, unique = true)
  private Long ownerUserId;

  @Column(name = "business_name", length = 200)
  private String businessName;

  @Column(length = 500)
  private String address;

  @Column(length = 40)
  private String phone;

  @Column(length = 160)
  private String email;

  @Column(length = 20)
  private String gstin;

  @Column(length = 80)
  private String state;

  /** A small base64 data URL — capped by the service so it stays cheap to read. */
  @Column(name = "logo_data_url", columnDefinition = "TEXT")
  private String logoDataUrl;

  @Column(name = "footer_note", length = 500)
  private String footerNote;
}
