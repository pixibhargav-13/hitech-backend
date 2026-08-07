package com.hitech.erp.tender.dto;

import java.util.List;

/** Request/response shapes for the Tender module. Records keep the wire format explicit. */
public final class TenderDtos {

  private TenderDtos() {}

  /** Full tender record returned to the client. Enum fields are surfaced as their String name. */
  public record TenderResponse(
      Long id,
      String source,
      String stage,
      String status,
      String statusLabel,
      String department,
      String tenderId,
      String nameOfWork,
      String location,
      String officeAddress,
      Double estimatedCost,
      Double contractValue,
      Double variancePct,
      String duration,
      Integer durationMonths,
      String deadline,
      String nextFollowUp,
      String dueDate,
      String submissionDate,
      String hardcopyDue,
      String techOpen,
      String priceOpen,
      String validity,
      Integer validityDays,
      String dlp,
      String openingDate,
      String preBidDate,
      Double fee,
      Double emd,
      String emdMode,
      String emdState,
      String emdPaidOn,
      String emdReleasedOn,
      String emdInstrumentNo,
      String emdExpiry,
      String pqCriteria,
      String classReq,
      String gst,
      String labTest,
      String priceEscalation,
      String depositDetails,
      String preBidInfo,
      String firm,
      String securityType,
      Double securityAmount,
      String additionalSecurityType,
      Double additionalSecurityAmount,
      Double bgCharges,
      String securityReleasedOn,
      String receivedStatus,
      String dateOfReceived,
      String priceBidStage,
      String lossReason,
      String lossReasonLabel,
      String lossNote,
      String l1Bidder,
      Double l1Value,
      Integer ourRank,
      String gemCategory,
      String msmeRelaxation,
      String experienceTurnover,
      String eligibilityStatus,
      String priority,
      String viewDocuments,
      String remarks,
      String customFields,
      Long projectId,
      String createdAt,
      String updatedAt) {}

  /**
   * Create/update payload. Every field is optional (nulls are ignored on update), so a tender can be
   * jotted down with just an id now and fleshed out later. {@code stage}/{@code status}/{@code source}
   * accept the enum names.
   */
  public record TenderRequest(
      String source,
      String stage,
      String status,
      String statusLabel,
      String department,
      String tenderId,
      String nameOfWork,
      String location,
      String officeAddress,
      Double estimatedCost,
      Double contractValue,
      Double variancePct,
      String duration,
      Integer durationMonths,
      String deadline,
      String nextFollowUp,
      String dueDate,
      String submissionDate,
      String hardcopyDue,
      String techOpen,
      String priceOpen,
      String validity,
      Integer validityDays,
      String dlp,
      String openingDate,
      String preBidDate,
      Double fee,
      Double emd,
      String emdMode,
      String emdState,
      String emdPaidOn,
      String emdReleasedOn,
      String emdInstrumentNo,
      String emdExpiry,
      String pqCriteria,
      String classReq,
      String gst,
      String labTest,
      String priceEscalation,
      String depositDetails,
      String preBidInfo,
      String firm,
      String securityType,
      Double securityAmount,
      String additionalSecurityType,
      Double additionalSecurityAmount,
      Double bgCharges,
      String securityReleasedOn,
      String receivedStatus,
      String dateOfReceived,
      String priceBidStage,
      String lossReason,
      String lossReasonLabel,
      String lossNote,
      String l1Bidder,
      Double l1Value,
      Integer ourRank,
      String gemCategory,
      String msmeRelaxation,
      String experienceTurnover,
      String eligibilityStatus,
      String priority,
      String viewDocuments,
      String remarks,
      String customFields,
      Long projectId) {}

  /** Move a tender to a new stage, optionally recording the applied-stage status. */
  public record StageChangeRequest(String stage, String status) {}

  /** A page of tenders, matching the shape the frontend list expects. */
  public record TenderPageResponse(
      List<TenderResponse> content,
      int page,
      int size,
      long totalElements,
      int totalPages) {}

  /** One stage's headline count for the dashboard. */
  public record StageCount(String stage, long count) {}

  /** Lightweight dashboard rollup: how many tenders sit in each stage, plus EMD exposure. */
  public record TenderSummary(
      long total,
      List<StageCount> byStage,
      double emdBlocked,
      double emdRecoverable) {}
}
