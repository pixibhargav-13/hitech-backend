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
      /** Brand/spec sub-line printed under the item name. */
      String specification,
      String hsnCode,
      String deliveryDate,
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
      /** BUYER = keyed in here, VENDOR = the supplier filled it in themselves. */
      String source,
      boolean locked,
      String submittedAt,
      List<QuoteLineResponse> lines) {}

  /** A supplier the enquiry went to, with contact details resolved from the party. */
  public record RfqSupplierResponse(
      Long id,
      Long vendorPartyId,
      String vendorName,
      String phone,
      String email,
      String sentAt,
      boolean responded,
      /** Present once the enquiry has been sent; this is the secret in their quote link. */
      String shareToken,
      /** When they last opened the link — "never opened" is a different problem from "no reply". */
      String openedAt) {}

  public record RfqResponse(
      Long id,
      String rfqNo,
      String title,
      Long projectId,
      String status,
      String rfqDate,
      String dueBy,
      /** ITEM = tax per line, BILL = one rate on the whole bill. */
      String taxType,
      String biddingStartDate,
      String biddingEndDate,
      String deliveryDate,
      String terms,
      String billToName,
      String billToAddress,
      String billToGstin,
      String shipToName,
      String shipToAddress,
      String shipToGstin,
      boolean shipSameAsBill,
      String notes,
      List<RfqLineResponse> lines,
      List<RfqSupplierResponse> suppliers,
      List<QuoteResponse> quotes) {}

  // ---- Write ----

  public record RfqLineRequest(
      Long id,
      Long itemId,
      @NotBlank String itemName,
      String specification,
      String hsnCode,
      String deliveryDate,
      String unit,
      BigDecimal quantity,
      BigDecimal budgetRate) {}

  public record RfqRequest(
      @NotBlank String title,
      /** Editable on the form; blank asks the server for the next running number. */
      String rfqNo,
      Long projectId,
      String status,
      String rfqDate,
      String dueBy,
      String taxType,
      String biddingStartDate,
      String biddingEndDate,
      String deliveryDate,
      String terms,
      String billToName,
      String billToAddress,
      String billToGstin,
      String shipToName,
      String shipToAddress,
      String shipToGstin,
      Boolean shipSameAsBill,
      String notes,
      List<RfqLineRequest> lines,
      /** Party ids of the suppliers to invite; replaces the current list wholesale. */
      List<Long> supplierPartyIds) {}

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

  /**
   * Send the enquiry: mint a quote link for each supplier and stamp them as sent. Passing no ids
   * sends to everyone on the enquiry who has not been sent to yet.
   */
  public record SendRequest(List<Long> supplierPartyIds) {}

  // ---- Supplier-facing (no login) ----

  /**
   * One line as the supplier sees it.
   *
   * <p>Deliberately not the internal line: {@code budgetRate} is what we are willing to pay, and
   * showing it to the people bidding against it would collapse every quote onto that number.
   */
  public record PublicRfqLine(
      Long id,
      String itemName,
      String specification,
      String hsnCode,
      String unit,
      BigDecimal quantity,
      String deliveryDate,
      /** Their own price from a previous submission, so the form reopens filled in. */
      BigDecimal rate,
      String note) {}

  public record PublicRfqResponse(
      String rfqNo,
      String title,
      String buyerName,
      String vendorName,
      String rfqDate,
      String biddingEndDate,
      String deliveryDate,
      String terms,
      String shipToName,
      String shipToAddress,
      /** False once the window has closed or the enquiry was closed off; the form goes read-only. */
      boolean acceptingQuotes,
      String closedReason,
      boolean alreadySubmitted,
      String submittedAt,
      Integer deliveryDays,
      BigDecimal discount,
      BigDecimal charges,
      BigDecimal taxPercent,
      String note,
      List<PublicRfqLine> lines) {}

  /** What the supplier posts back. No vendor id — the token says who they are. */
  public record PublicQuoteRequest(
      Integer deliveryDays,
      BigDecimal discount,
      BigDecimal charges,
      BigDecimal taxPercent,
      String note,
      List<QuoteLineRequest> lines) {}

  /** Award one line, or clear it by passing a null vendor. */
  public record AwardRequest(Long vendorPartyId, String reason) {}
}
