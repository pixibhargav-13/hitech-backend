package com.hitech.erp.vyapar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Request/response shapes for Cash &amp; Bank and the firm profile that brands documents. */
public final class CashBankDtos {

  private CashBankDtos() {}

  // ---- Bank account ----
  public record BankAccountResponse(
      Long id,
      String name,
      BigDecimal openingBalance,
      String openingDate,
      String accountNumber,
      String ifsc,
      String bankName,
      String accountHolder,
      String upiId,
      boolean printUpiQr,
      boolean printBankDetails,
      boolean isActive,
      /** Opening balance plus every posted movement. */
      BigDecimal balance) {}

  public record BankAccountRequest(
      @NotBlank String name,
      BigDecimal openingBalance,
      String openingDate,
      String accountNumber,
      String ifsc,
      String bankName,
      String accountHolder,
      String upiId,
      Boolean printUpiQr,
      Boolean printBankDetails,
      Boolean isActive) {}

  // ---- Movement ----
  public record CashBankTxnResponse(
      Long id, Long accountId, String type, String name, String date, BigDecimal amount, String direction, String note) {}

  /** Bank↔cash, bank↔bank transfers and manual balance adjustments — one call, one or two rows written. */
  public record CashBankEntryRequest(
      @NotBlank String kind, // BANK_TO_CASH | CASH_TO_BANK | BANK_TO_BANK | ADJUST_BANK | ADJUST_CASH
      Long fromAccountId,
      Long toAccountId,
      @NotNull BigDecimal amount,
      String date,
      String description,
      String mode // ADD or REDUCE, for the ADJUST_* kinds
      ) {}

  // ---- Cheque ----
  public record ChequeResponse(
      Long id,
      String chequeNo,
      String partyName,
      String invoiceNo,
      String direction,
      BigDecimal amount,
      String chequeDate,
      String transferDate,
      String status,
      Long accountId) {}

  public record ChequeUpdateRequest(String status, String transferDate, Long accountId) {}

  // ---- Loan ----
  public record LoanAccountResponse(
      Long id,
      String name,
      String lender,
      String accountNumber,
      BigDecimal loanAmount,
      BigDecimal balance,
      BigDecimal interestRate,
      int termMonths,
      String startDate,
      BigDecimal emiAmount) {}

  public record LoanAccountRequest(
      @NotBlank String name,
      String lender,
      String accountNumber,
      BigDecimal loanAmount,
      BigDecimal balance,
      BigDecimal interestRate,
      Integer termMonths,
      String startDate,
      BigDecimal emiAmount) {}

  // ---- Firm profile ----
  public record FirmProfileResponse(
      String businessName, String address, String phone, String email, String gstin, String state, String logoDataUrl, String footerNote) {}

  public record FirmProfileRequest(
      String businessName, String address, String phone, String email, String gstin, String state, String logoDataUrl, String footerNote) {}
}
