package com.hitech.erp.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

/**
 * Wire shapes for work orders.
 *
 * <p>Every derived figure — order value, billed, outstanding, physical progress — is computed on
 * the server and sent down. The list shows six of them at once across twenty orders; making the
 * browser re-derive them from lines and bills would be the same arithmetic done in a place where a
 * rounding difference would be invisible.
 */
public final class WorkOrderDtos {

  private WorkOrderDtos() {}

  // ---- Read ----

  public record WorkOrderItemResponse(
      Long id,
      Long itemId,
      String itemName,
      String description,
      String unit,
      /** N x L x W x H, as measured. Null where the line was not measured that way. */
      BigDecimal dimN,
      BigDecimal dimL,
      BigDecimal dimW,
      BigDecimal dimH,
      BigDecimal quantity,
      BigDecimal rate,
      BigDecimal amount,
      BigDecimal progressPercent,
      int sortOrder) {}

  public record SubconBillResponse(
      Long id,
      String billNo,
      String billDate,
      BigDecimal amount,
      BigDecimal retention,
      BigDecimal materialRecovery,
      /** What he is actually paid on this bill: amount less retention and recovery. */
      BigDecimal netPayable,
      String note,
      Long vyaparInvoiceId) {}

  public record SubconMaterialResponse(
      Long id,
      Long itemId,
      String itemName,
      String unit,
      String movement,
      BigDecimal quantity,
      BigDecimal rate,
      String movedOn,
      String note) {}

  /** One row of the Materials tab: a material, rolled up across its movements. */
  public record SubconMaterialSummary(
      String itemName,
      String unit,
      /** Everything issued to him, ever. */
      BigDecimal totalIssued,
      BigDecimal returned,
      BigDecimal consumed,
      /** What he still holds: issued less returned less consumed. */
      BigDecimal inHand,
      /** Value of what was issued, at the recovery rate — what comes off his bill. */
      BigDecimal issuedValue) {}

  public record WorkOrderResponse(
      Long id,
      String woNo,
      String title,
      Long projectId,
      Long vendorPartyId,
      String vendorName,
      String vendorPhone,
      String status,
      String woDate,
      String startDate,
      String endDate,
      BigDecimal taxPercent,
      BigDecimal discount,
      BigDecimal charges,
      String bankAccountName,
      String bankAccountNumber,
      String bankIfsc,
      String terms,
      String notes,
      // ---- derived ----
      /** Items totalled, before whole-order terms. */
      BigDecimal itemSubTotal,
      BigDecimal taxAmount,
      /** What the order is worth: items less discount plus charges plus tax. */
      BigDecimal orderValue,
      /** Weighted by line value — a 90%-done line worth ₹4L counts for more than a 100%-done ₹3k one. */
      BigDecimal physicalProgress,
      /** Order value earned so far, at that progress. */
      BigDecimal workDoneValue,
      BigDecimal billedValue,
      /** Order value not yet billed. Negative means he has billed past the order. */
      BigDecimal outstanding,
      BigDecimal retentionHeld,
      BigDecimal materialIssuedValue,
      List<WorkOrderItemResponse> items,
      List<SubconBillResponse> bills,
      List<SubconMaterialResponse> materials,
      List<SubconMaterialSummary> materialSummary) {}

  // ---- Write ----

  public record WorkOrderItemRequest(
      Long id,
      Long itemId,
      @NotBlank String itemName,
      String description,
      String unit,
      BigDecimal dimN,
      BigDecimal dimL,
      BigDecimal dimW,
      BigDecimal dimH,
      /** Ignored when the dimensions are given — the measurement wins over a typed figure. */
      BigDecimal quantity,
      BigDecimal rate,
      BigDecimal progressPercent) {}

  public record WorkOrderRequest(
      @NotBlank String title,
      /** Editable on the form; blank asks the server for the next running number. */
      String woNo,
      Long projectId,
      Long vendorPartyId,
      String status,
      String woDate,
      String startDate,
      String endDate,
      BigDecimal taxPercent,
      BigDecimal discount,
      BigDecimal charges,
      String bankAccountName,
      String bankAccountNumber,
      String bankIfsc,
      String terms,
      String notes,
      List<WorkOrderItemRequest> items) {}

  public record SubconBillRequest(
      Long id,
      String billNo,
      String billDate,
      BigDecimal amount,
      BigDecimal retention,
      BigDecimal materialRecovery,
      String note,
      Long vyaparInvoiceId) {}

  public record SubconMaterialRequest(
      Long id,
      Long itemId,
      @NotBlank String itemName,
      String unit,
      String movement,
      BigDecimal quantity,
      BigDecimal rate,
      String movedOn,
      String note) {}

  /** Update one line's physical progress — the figure the whole order's progress is derived from. */
  public record ProgressRequest(BigDecimal progressPercent) {}
}
