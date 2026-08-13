package com.hitech.erp.vyapar.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

/** Request/response shapes for the Vyapar module. Records keep the wire format explicit. */
public final class VyaparDtos {

  private VyaparDtos() {}

  // ---- Party ----
  public record PartyResponse(
      Long id,
      String name,
      String partyType,
      String phone,
      String email,
      String gstin,
      String gstType,
      String state,
      String billingAddress,
      String shippingAddress,
      String city,
      String partyGroup,
      BigDecimal openingBalance,
      String openingDate,
      BigDecimal creditLimit,
      String field1,
      String field2,
      String field3,
      String field4,
      boolean isActive,
      Long bankAccountId,
      /** Opening balance plus every posted document and payment. */
      BigDecimal balance) {}

  /** One row of a party's ledger — documents and payments interleaved, newest first. */
  public record PartyLedgerRow(
      Long id,
      String kind,
      String type,
      String number,
      String date,
      BigDecimal total,
      BigDecimal balance,
      String status) {}

  public record PartyRequest(
      @NotBlank String name,
      String partyType,
      String phone,
      String email,
      String gstin,
      String gstType,
      String state,
      String billingAddress,
      String shippingAddress,
      String city,
      String partyGroup,
      BigDecimal openingBalance,
      String openingDate,
      BigDecimal creditLimit,
      String field1,
      String field2,
      String field3,
      String field4,
      Boolean isActive,
      Long bankAccountId) {}

  // ---- Item ----
  public record ItemResponse(
      Long id,
      String name,
      String category,
      String description,
      String itemCode,
      String hsn,
      String unit,
      BigDecimal salePrice,
      BigDecimal purchasePrice,
      BigDecimal taxPercent,
      BigDecimal stockQty,
      BigDecimal lowStockAlert,
      boolean isService,
      /** Item photo as a data URL, or null. */
      String imageDataUrl,
      boolean isActive,
      Long bankAccountId,
      /** stockQty * purchasePrice — what the shelf is worth. */
      BigDecimal stockValue,
      boolean lowStock) {}

  /** A single movement in an item's stock ledger — a sale/purchase line or a manual adjustment. */
  public record ItemLedgerRow(
      Long id,
      String type,
      String ref,
      String name,
      String date,
      BigDecimal quantity,
      BigDecimal pricePerUnit,
      String status) {}

  public record StockAdjustRequest(
      @NotBlank String mode, // ADD or REDUCE
      BigDecimal quantity,
      BigDecimal atPrice,
      String date,
      String note) {}

  public record ItemRequest(
      @NotBlank String name,
      String category,
      String description,
      String itemCode,
      String hsn,
      String unit,
      BigDecimal salePrice,
      BigDecimal purchasePrice,
      BigDecimal taxPercent,
      BigDecimal stockQty,
      BigDecimal lowStockAlert,
      Boolean isService,
      String imageDataUrl,
      Boolean isActive,
      Long bankAccountId) {}

  // ---- Invoice ----
  public record InvoiceLineDto(
      Long id,
      Long itemId,
      String itemName,
      String description,
      String unit,
      BigDecimal quantity,
      BigDecimal rate,
      BigDecimal discountPercent,
      BigDecimal discountAmount,
      BigDecimal taxPercent,
      BigDecimal taxAmount,
      BigDecimal amount) {}

  public record InvoiceResponse(
      Long id,
      String docType,
      String invoiceNo,
      Long partyId,
      String partyName,
      String invoiceDate,
      String dueDate,
      BigDecimal subTotal,
      BigDecimal discount,
      BigDecimal taxAmount,
      BigDecimal total,
      BigDecimal paidAmount,
      BigDecimal balance,
      String paymentType,
      /** Cheque / NEFT number for the amount received with the document. */
      String paymentReference,
      /** Walk-in details for a cash bill with no saved party behind it. */
      String billingName,
      String billingAddress,
      boolean isCash,
      String stateOfSupply,
      String invoicePrefix,
      String terms,
      BigDecimal discountPercent,
      BigDecimal roundOff,
      /** Paid / Partial / Unpaid, or Cancelled — cancelled wins over any balance. */
      String status,
      boolean cancelled,
      String notes,
      Long bankAccountId,
      Long projectId,
      List<InvoiceLineDto> lines) {}

  public record InvoiceLineRequest(
      Long itemId,
      @NotBlank String itemName,
      String description,
      String unit,
      BigDecimal quantity,
      BigDecimal rate,
      BigDecimal discountPercent,
      BigDecimal discountAmount,
      BigDecimal taxPercent) {}

  public record InvoiceRequest(
      String docType,
      String invoiceNo,
      Long partyId,
      String invoiceDate,
      String dueDate,
      BigDecimal discount,
      BigDecimal discountPercent,
      BigDecimal paidAmount,
      String paymentType,
      String paymentReference,
      String billingName,
      String billingAddress,
      Boolean isCash,
      String stateOfSupply,
      String invoicePrefix,
      String terms,
      BigDecimal roundOff,
      String notes,
      Long bankAccountId,
      Long projectId,
      List<InvoiceLineRequest> lines) {}

  // ---- Payment ----
  public record PaymentResponse(
      Long id,
      String direction,
      Long partyId,
      String partyName,
      Long invoiceId,
      String paymentDate,
      BigDecimal amount,
      String mode,
      String reference,
      String notes,
      Long bankAccountId,
      Long projectId,
      /** Sum of this payment's links; amount − linkedAmount is what shows as "Unused". */
      BigDecimal linkedAmount,
      BigDecimal unusedAmount,
      List<PaymentLinkDto> links) {}

  public record PaymentRequest(
      String direction,
      Long partyId,
      Long invoiceId,
      String paymentDate,
      BigDecimal amount,
      String mode,
      String reference,
      String notes,
      Long bankAccountId,
      Long projectId,
      /** Vyapar's "Link Payment to Txns" — how this receipt is spread across open documents. */
      List<PaymentLinkDto> links) {}

  /** How much of a payment is applied to one document. */
  public record PaymentLinkDto(Long invoiceId, String invoiceNo, String docType, BigDecimal amount) {}

  /** An open document offered in the Link Payment dialog. */
  public record OpenTxnRow(
      Long id,
      String docType,
      String type,
      String number,
      String date,
      BigDecimal total,
      /** Still outstanding after every link already recorded against it. */
      BigDecimal balance,
      /** How much of *this* payment is currently linked to it. */
      BigDecimal linkedAmount) {}

  /** One line of a document's audit trail. */
  public record InvoiceHistoryRow(Long id, String action, String detail, Long userId, String at) {}

  // ---- Settings ----
  public record SettingsDto(
      int amountDecimals,
      int quantityDecimals,
      boolean roundOffEnabled,
      String roundOffMode,
      int roundOffTo,
      boolean dueDatesEnabled,
      boolean linkPaymentsEnabled,
      boolean itemWiseTax,
      boolean itemWiseDiscount,
      boolean displayPurchasePrice,
      boolean transactionWiseTax,
      boolean transactionWiseDiscount,
      boolean estimateEnabled,
      boolean proformaEnabled,
      boolean ordersEnabled,
      boolean deliveryChallanEnabled,
      String prefixes) {}

  // ---- Dashboard ----
  public record DashboardPoint(String label, BigDecimal value) {}

  public record DashboardSummary(
      BigDecimal totalReceivable,
      long receivableParties,
      BigDecimal totalPayable,
      long payableParties,
      BigDecimal totalSale,
      BigDecimal totalPurchase,
      BigDecimal cashInHand,
      long stockItems,
      BigDecimal stockValue,
      long lowStockCount,
      List<DashboardPoint> salesTrend) {}
}
