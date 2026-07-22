package com.hitech.erp.vyapar.api;

import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import com.hitech.erp.vyapar.dto.VyaparDtos.*;
import com.hitech.erp.vyapar.service.VyaparService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** REST surface for the Vyapar billing module. Everything is gated behind VYAPAR:* permissions. */
@RestController
@RequestMapping("/api/v1/vyapar")
@RequiredArgsConstructor
public class VyaparController {

  private final VyaparService service;

  private static Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) ? u.id() : null;
  }

  // ---- Dashboard ----
  @GetMapping("/dashboard")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<DashboardSummary> dashboard(@RequestParam(name = "bankAccountId", required = false) Long bankAccountId) {
    return ResponseEntity.ok(service.getDashboard(bankAccountId));
  }

  // ---- Parties ----
  @GetMapping("/parties")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<List<PartyResponse>> getParties(
      @RequestParam(name = "type", required = false) String type,
      @RequestParam(name = "bankAccountId", required = false) Long bankAccountId) {
    return ResponseEntity.ok(service.getParties(type, bankAccountId));
  }

  @GetMapping("/parties/{id}")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<PartyResponse> getParty(@PathVariable("id") Long id) {
    return ResponseEntity.ok(service.getParty(id));
  }

  @PostMapping("/parties")
  @PreAuthorize("hasAuthority('VYAPAR:CREATE')")
  public ResponseEntity<PartyResponse> createParty(@Valid @RequestBody PartyRequest r) {
    return ResponseEntity.ok(service.createParty(r));
  }

  @PutMapping("/parties/{id}")
  @PreAuthorize("hasAuthority('VYAPAR:EDIT')")
  public ResponseEntity<PartyResponse> updateParty(@PathVariable("id") Long id, @Valid @RequestBody PartyRequest r) {
    return ResponseEntity.ok(service.updateParty(id, r));
  }

  @GetMapping("/parties/{id}/ledger")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<List<PartyLedgerRow>> partyLedger(@PathVariable("id") Long id) {
    return ResponseEntity.ok(service.partyLedger(id));
  }

  /** Bulk create parties from an imported sheet. */
  @PostMapping("/parties/import")
  @PreAuthorize("hasAuthority('VYAPAR:CREATE')")
  public ResponseEntity<List<PartyResponse>> importParties(@RequestBody PartyImportRequest request) {
    return ResponseEntity.ok(service.importParties(request.rows()));
  }

  public record PartyImportRequest(List<PartyRequest> rows) {}

  @DeleteMapping("/parties/{id}")
  @PreAuthorize("hasAuthority('VYAPAR:DELETE')")
  public ResponseEntity<Void> deleteParty(@PathVariable("id") Long id) {
    service.deleteParty(id);
    return ResponseEntity.noContent().build();
  }

  // ---- Items ----
  @GetMapping("/items")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<List<ItemResponse>> getItems(@RequestParam(name = "bankAccountId", required = false) Long bankAccountId) {
    return ResponseEntity.ok(service.getItems(bankAccountId));
  }

  @PostMapping("/items")
  @PreAuthorize("hasAuthority('VYAPAR:CREATE')")
  public ResponseEntity<ItemResponse> createItem(@Valid @RequestBody ItemRequest r) {
    return ResponseEntity.ok(service.createItem(r));
  }

  @PutMapping("/items/{id}")
  @PreAuthorize("hasAuthority('VYAPAR:EDIT')")
  public ResponseEntity<ItemResponse> updateItem(@PathVariable("id") Long id, @Valid @RequestBody ItemRequest r) {
    return ResponseEntity.ok(service.updateItem(id, r));
  }

  @DeleteMapping("/items/{id}")
  @PreAuthorize("hasAuthority('VYAPAR:DELETE')")
  public ResponseEntity<Void> deleteItem(@PathVariable("id") Long id) {
    service.deleteItem(id);
    return ResponseEntity.noContent().build();
  }

  /** Bulk create items from an imported sheet. */
  @PostMapping("/items/import")
  @PreAuthorize("hasAuthority('VYAPAR:CREATE')")
  public ResponseEntity<List<ItemResponse>> importItems(@RequestBody ItemImportRequest request) {
    return ResponseEntity.ok(service.importItems(request.rows()));
  }

  public record ItemImportRequest(List<ItemRequest> rows) {}

  @GetMapping("/items/{id}/ledger")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<List<ItemLedgerRow>> itemLedger(@PathVariable("id") Long id) {
    return ResponseEntity.ok(service.itemLedger(id));
  }

  @PostMapping("/items/{id}/adjust")
  @PreAuthorize("hasAuthority('VYAPAR:EDIT')")
  public ResponseEntity<ItemResponse> adjustStock(@PathVariable("id") Long id, @Valid @RequestBody StockAdjustRequest r) {
    return ResponseEntity.ok(service.adjustStock(id, r));
  }

  // ---- Invoices (sale, purchase, estimate, …) ----
  @GetMapping("/invoices")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<List<InvoiceResponse>> getInvoices(
      @RequestParam(name = "docType", required = false) String docType,
      @RequestParam(name = "bankAccountId", required = false) Long bankAccountId) {
    return ResponseEntity.ok(service.getInvoices(docType, bankAccountId));
  }

  @GetMapping("/invoices/{id}")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable("id") Long id) {
    return ResponseEntity.ok(service.getInvoice(id));
  }

  @PostMapping("/invoices")
  @PreAuthorize("hasAuthority('VYAPAR:CREATE')")
  public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody InvoiceRequest r) {
    return ResponseEntity.ok(service.createInvoice(r, currentUserId()));
  }

  @PutMapping("/invoices/{id}")
  @PreAuthorize("hasAuthority('VYAPAR:EDIT')")
  public ResponseEntity<InvoiceResponse> updateInvoice(@PathVariable("id") Long id, @Valid @RequestBody InvoiceRequest r) {
    return ResponseEntity.ok(service.updateInvoice(id, r));
  }

  @DeleteMapping("/invoices/{id}")
  @PreAuthorize("hasAuthority('VYAPAR:DELETE')")
  public ResponseEntity<Void> deleteInvoice(@PathVariable("id") Long id) {
    service.deleteInvoice(id);
    return ResponseEntity.noContent().build();
  }

  // ---- Payments ----
  @GetMapping("/payments")
  @PreAuthorize("hasAuthority('VYAPAR:VIEW')")
  public ResponseEntity<List<PaymentResponse>> getPayments(
      @RequestParam(name = "direction", required = false) String direction,
      @RequestParam(name = "bankAccountId", required = false) Long bankAccountId) {
    return ResponseEntity.ok(service.getPayments(direction, bankAccountId));
  }

  @PostMapping("/payments")
  @PreAuthorize("hasAuthority('VYAPAR:CREATE')")
  public ResponseEntity<PaymentResponse> createPayment(@RequestBody PaymentRequest r) {
    return ResponseEntity.ok(service.createPayment(r));
  }

  @DeleteMapping("/payments/{id}")
  @PreAuthorize("hasAuthority('VYAPAR:DELETE')")
  public ResponseEntity<Void> deletePayment(@PathVariable("id") Long id) {
    service.deletePayment(id);
    return ResponseEntity.noContent().build();
  }
}
