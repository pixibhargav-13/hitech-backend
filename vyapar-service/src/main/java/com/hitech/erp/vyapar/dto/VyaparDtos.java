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
      boolean isCash,
      String stateOfSupply,
      String invoicePrefix,
      String terms,
      BigDecimal discountPercent,
      BigDecimal roundOff,
      String status,
      String notes,
      Long bankAccountId,
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
      Boolean isCash,
      String stateOfSupply,
      String invoicePrefix,
      String terms,
      BigDecimal roundOff,
      String notes,
      Long bankAccountId,
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
      Long bankAccountId) {}

  public record PaymentRequest(
      String direction,
      Long partyId,
      Long invoiceId,
      String paymentDate,
      BigDecimal amount,
      String mode,
      String reference,
      String notes,
      Long bankAccountId) {}

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
