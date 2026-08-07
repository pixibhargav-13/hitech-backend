package com.hitech.erp.tender.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Documentation-tracker record for a won tender. Document categories are user-editable, so soft/hard
 * status is kept as JSON ({@code {"letterOfAcceptance":{"soft":true,"hard":false},...}}); running-
 * account bills are an unbounded JSON array of soft/hard pairs.
 */
@Getter
@Setter
@Entity
@Table(name = "tender_documents")
public class TenderDocumentEntity extends BaseEntity {

  @Column(name = "tender_ref", length = 120)
  private String tenderRef;

  @Column(name = "name_of_work", length = 2000)
  private String nameOfWork;

  @Column(length = 200)
  private String progress;

  @Column(name = "view_documents", length = 1000)
  private String viewDocuments;

  /** Per-document-type soft/hard status. JSON object. */
  @Column(name = "pairs_json", columnDefinition = "text")
  private String pairsJson;

  /** Running-account bills — JSON array of {soft,hard} pairs. */
  @Column(name = "ra_bills_json", columnDefinition = "text")
  private String raBillsJson;
}
