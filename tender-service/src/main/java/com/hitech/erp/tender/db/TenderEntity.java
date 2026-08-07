package com.hitech.erp.tender.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One tender moving through the bidding pipeline (Sorting → Research → Applied → Won/Lost). Mirrors
 * the frontend Tender model; winning hands off to the Project module (see {@code projectId}). Dates
 * are stored as strings because the source workbook mixes formats and the UI treats them as opaque.
 */
@Getter
@Setter
@Entity
@Table(name = "tenders")
public class TenderEntity extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TenderSource source = TenderSource.PORTAL;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TenderStage stage = TenderStage.SORTING;

  @Enumerated(EnumType.STRING)
  @Column(length = 30)
  private TenderStatus status;

  @Column(name = "status_label", length = 120)
  private String statusLabel;

  @Column(length = 300)
  private String department;

  @Column(name = "tender_id", nullable = false, length = 120)
  private String tenderId = "";

  @Column(name = "name_of_work", length = 2000)
  private String nameOfWork;

  @Column(length = 300)
  private String location;

  @Column(name = "office_address", length = 1000)
  private String officeAddress;

  @Column(name = "estimated_cost")
  private Double estimatedCost;

  @Column(name = "contract_value")
  private Double contractValue;

  @Column(name = "variance_pct")
  private Double variancePct;

  @Column(length = 120)
  private String duration;

  @Column(name = "duration_months")
  private Integer durationMonths;

  @Column(length = 30)
  private String deadline;

  @Column(name = "next_follow_up", length = 30)
  private String nextFollowUp;

  @Column(name = "due_date", length = 30)
  private String dueDate;

  @Column(name = "submission_date", length = 30)
  private String submissionDate;

  @Column(name = "hardcopy_due", length = 30)
  private String hardcopyDue;

  @Column(name = "tech_open", length = 60)
  private String techOpen;

  @Column(name = "price_open", length = 60)
  private String priceOpen;

  @Column(length = 60)
  private String validity;

  @Column(name = "validity_days")
  private Integer validityDays;

  @Column(length = 60)
  private String dlp;

  @Column(name = "opening_date", length = 30)
  private String openingDate;

  @Column(name = "pre_bid_date", length = 30)
  private String preBidDate;

  private Double fee;

  private Double emd;

  @Column(name = "emd_mode", length = 20)
  private String emdMode;

  @Column(name = "emd_state", length = 20)
  private String emdState;

  @Column(name = "emd_paid_on", length = 30)
  private String emdPaidOn;

  @Column(name = "emd_released_on", length = 30)
  private String emdReleasedOn;

  @Column(name = "emd_instrument_no", length = 120)
  private String emdInstrumentNo;

  @Column(name = "emd_expiry", length = 30)
  private String emdExpiry;

  @Column(name = "pq_criteria", length = 2000)
  private String pqCriteria;

  @Column(name = "class_req", length = 200)
  private String classReq;

  @Column(length = 120)
  private String gst;

  @Column(name = "lab_test", length = 500)
  private String labTest;

  @Column(name = "price_escalation", length = 500)
  private String priceEscalation;

  @Column(name = "deposit_details", length = 1000)
  private String depositDetails;

  @Column(name = "pre_bid_info", length = 1000)
  private String preBidInfo;

  @Column(length = 200)
  private String firm;

  @Column(name = "security_type", length = 20)
  private String securityType;

  @Column(name = "security_amount")
  private Double securityAmount;

  @Column(name = "additional_security_type", length = 20)
  private String additionalSecurityType;

  @Column(name = "additional_security_amount")
  private Double additionalSecurityAmount;

  @Column(name = "bg_charges")
  private Double bgCharges;

  @Column(name = "security_released_on", length = 30)
  private String securityReleasedOn;

  @Column(name = "received_status", length = 120)
  private String receivedStatus;

  @Column(name = "date_of_received", length = 30)
  private String dateOfReceived;

  @Column(name = "price_bid_stage", length = 120)
  private String priceBidStage;

  @Column(name = "loss_reason", length = 40)
  private String lossReason;

  @Column(name = "loss_reason_label", length = 200)
  private String lossReasonLabel;

  @Column(name = "loss_note", length = 2000)
  private String lossNote;

  @Column(name = "l1_bidder", length = 200)
  private String l1Bidder;

  @Column(name = "l1_value")
  private Double l1Value;

  @Column(name = "our_rank")
  private Integer ourRank;

  @Column(name = "gem_category", length = 200)
  private String gemCategory;

  @Column(name = "msme_relaxation", length = 200)
  private String msmeRelaxation;

  @Column(name = "experience_turnover", length = 500)
  private String experienceTurnover;

  @Column(name = "eligibility_status", length = 200)
  private String eligibilityStatus;

  @Column(length = 10)
  private String priority;

  @Column(name = "view_documents", length = 1000)
  private String viewDocuments;

  @Column(length = 4000)
  private String remarks;

  /** User-defined extra fields, stored as a JSON string ([{id,label,value}]). */
  @Column(name = "custom_fields", columnDefinition = "text")
  private String customFields;

  /** Set once a WON tender has been promoted into the Project module. */
  @Column(name = "project_id")
  private Long projectId;

  /** JWT user id of whoever created the record. */
  @Column(name = "created_by")
  private Long createdBy;
}
