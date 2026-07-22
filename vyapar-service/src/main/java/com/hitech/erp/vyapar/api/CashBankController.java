package com.hitech.erp.vyapar.api;

import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import com.hitech.erp.vyapar.dto.CashBankDtos.*;
import com.hitech.erp.vyapar.service.CashBankService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** Cash &amp; Bank and the firm profile — every row here is private to the calling user. */
@RestController
@RequestMapping("/api/v1/vyapar")
@RequiredArgsConstructor
public class CashBankController {

  private final CashBankService service;

  private static Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth != null && auth.getPrincipal() instanceof AuthenticatedUser u)) {
      throw new IllegalStateException("No authenticated user");
    }
    return u.id();
  }

  // ---- Bank accounts ----
  @GetMapping("/bank-accounts")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<List<BankAccountResponse>> getBankAccounts() {
    return ResponseEntity.ok(service.getBankAccounts(currentUserId()));
  }

  @PostMapping("/bank-accounts")
  @PreAuthorize("hasAuthority('VYAPAR:CREATE')")
  public ResponseEntity<BankAccountResponse> createBankAccount(@Valid @RequestBody BankAccountRequest r) {
    return ResponseEntity.ok(service.createBankAccount(currentUserId(), r));
  }

  @PutMapping("/bank-accounts/{id}")
  @PreAuthorize("hasAuthority('VYAPAR:EDIT')")
  public ResponseEntity<BankAccountResponse> updateBankAccount(@PathVariable("id") Long id, @Valid @RequestBody BankAccountRequest r) {
    return ResponseEntity.ok(service.updateBankAccount(currentUserId(), id, r));
  }

  @DeleteMapping("/bank-accounts/{id}")
  @PreAuthorize("hasAuthority('VYAPAR:DELETE')")
  public ResponseEntity<Void> deleteBankAccount(@PathVariable("id") Long id) {
    service.deleteBankAccount(currentUserId(), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/bank-accounts/{id}/transactions")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<List<CashBankTxnResponse>> getAccountTxns(@PathVariable("id") Long id) {
    return ResponseEntity.ok(service.getAccountTxns(currentUserId(), id));
  }

  // ---- Cash in hand ----
  @GetMapping("/cash-in-hand")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<List<CashBankTxnResponse>> getCashTxns() {
    return ResponseEntity.ok(service.getCashTxns(currentUserId()));
  }

  // ---- Entries ----
  @PostMapping("/cash-bank/entries")
  @PreAuthorize("hasAuthority('VYAPAR:CREATE')")
  public ResponseEntity<CashBankTxnResponse> postEntry(@Valid @RequestBody CashBankEntryRequest r) {
    return ResponseEntity.ok(service.postCashBankEntry(currentUserId(), r));
  }

  // ---- Cheques ----
  @GetMapping("/cheques")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<List<ChequeResponse>> getCheques() {
    return ResponseEntity.ok(service.getCheques(currentUserId()));
  }

  @PutMapping("/cheques/{id}")
  @PreAuthorize("hasAuthority('VYAPAR:EDIT')")
  public ResponseEntity<ChequeResponse> updateCheque(@PathVariable("id") Long id, @RequestBody ChequeUpdateRequest r) {
    return ResponseEntity.ok(service.updateCheque(currentUserId(), id, r));
  }

  // ---- Loans ----
  @GetMapping("/loan-accounts")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<List<LoanAccountResponse>> getLoanAccounts() {
    return ResponseEntity.ok(service.getLoanAccounts(currentUserId()));
  }

  @PostMapping("/loan-accounts")
  @PreAuthorize("hasAuthority('VYAPAR:CREATE')")
  public ResponseEntity<LoanAccountResponse> createLoanAccount(@Valid @RequestBody LoanAccountRequest r) {
    return ResponseEntity.ok(service.createLoanAccount(currentUserId(), r));
  }

  // ---- Firm profile ----
  @GetMapping("/firm-profile")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<FirmProfileResponse> getFirmProfile() {
    return ResponseEntity.ok(service.getFirmProfile(currentUserId()));
  }

  @PutMapping("/firm-profile")
  @PreAuthorize("hasAuthority('VYAPAR:EDIT')")
  public ResponseEntity<FirmProfileResponse> updateFirmProfile(@RequestBody FirmProfileRequest r) {
    return ResponseEntity.ok(service.updateFirmProfile(currentUserId(), r));
  }
}
