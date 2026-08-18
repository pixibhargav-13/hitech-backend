package com.hitech.erp.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

/**
 * Wire shapes for Procurement.
 *
 * <p>Vendor names are resolved and sent alongside the party id: the comparison screen shows a
 * dozen vendor columns at once, and making the browser fetch the party list to label them would be
 * a second round trip for something the server already has.
 */
public final class ProcurementDtos {

  private ProcurementDtos() {}

  // ---- Read ----

  public record RfqLineResponse(
      Long id,
      Long itemId,
      String itemName,
      String unit,
      BigDecimal quantity,
      BigDecimal budgetRate,
      Long awardedVendorPartyId,
      String awardedVendorName,
      String awardReason,
      int sortOrder) {}

  /** One vendor's price for one line. `rate` null means they did not quote it. */
  public record QuoteLineResponse(Long rfqLineId, BigDecimal rate, BigDecimal quantity, String note) {}

  public record QuoteResponse(
      Long id,
      Long vendorPartyId,
      String vendorName,
      int version,
      String receivedOn,
      Integer deliveryDays,
      BigDecimal discount,
      BigDecimal charges,
      BigDecimal taxPercent,
      String note,
      List<QuoteLineResponse> lines) {}

  public record RfqResponse(
      Long id,
      String rfqNo,
      String title,
      Long projectId,
      String status,
      String rfqDate,
      String dueBy,
      String notes,
      List<RfqLineResponse> lines,
      List<QuoteResponse> quotes) {}

  // ---- Write ----

  public record RfqLineRequest(
      Long id,
      Long itemId,
      @NotBlank String itemName,
      String unit,
      BigDecimal quantity,
      BigDecimal budgetRate) {}

  public record RfqRequest(
      @NotBlank String title,
      Long projectId,
      String status,
      String rfqDate,
      String dueBy,
      String notes,
      List<RfqLineRequest> lines) {}

  public record QuoteLineRequest(Long rfqLineId, BigDecimal rate, BigDecimal quantity, String note) {}

  public record QuoteRequest(
      Long vendorPartyId,
      String receivedOn,
      Integer deliveryDays,
      BigDecimal discount,
      BigDecimal charges,
      BigDecimal taxPercent,
      String note,
      List<QuoteLineRequest> lines) {}

  /** Award one line, or clear it by passing a null vendor. */
  public record AwardRequest(Long vendorPartyId, String reason) {}
}
