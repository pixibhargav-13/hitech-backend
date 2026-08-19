package com.hitech.erp.procurement.api;

import com.hitech.erp.procurement.dto.ProcurementDtos.AwardRequest;
import com.hitech.erp.procurement.dto.ProcurementDtos.QuoteRequest;
import com.hitech.erp.procurement.dto.ProcurementDtos.RfqRequest;
import com.hitech.erp.procurement.dto.ProcurementDtos.RfqResponse;
import com.hitech.erp.procurement.dto.ProcurementDtos.SendRequest;
import com.hitech.erp.procurement.service.RfqService;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * REST surface for Procurement.
 *
 * <p>Gated behind PROCUREMENT:* like every other module. Awarding is its own endpoint rather than a
 * field on the RFQ update: it is a decision with money behind it, and giving it a distinct route
 * keeps it separately auditable and separately permissioned from editing the enquiry text.
 */
@RestController
@RequestMapping("/api/v1/procurement/rfqs")
@RequiredArgsConstructor
public class RfqController {

  private final RfqService service;

  private static Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) ? u.id() : null;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PROCUREMENT:VIEW')")
  public ResponseEntity<List<RfqResponse>> getRfqs(
      @RequestParam(name = "projectId", required = false) Long projectId) {
    return ResponseEntity.ok(service.getRfqs(projectId));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PROCUREMENT:VIEW')")
  public ResponseEntity<RfqResponse> getRfq(@PathVariable("id") Long id) {
    return ResponseEntity.ok(service.getRfq(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PROCUREMENT:CREATE')")
  public ResponseEntity<RfqResponse> create(@Valid @RequestBody RfqRequest r) {
    return ResponseEntity.ok(service.create(r, currentUserId()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PROCUREMENT:EDIT')")
  public ResponseEntity<RfqResponse> update(@PathVariable("id") Long id, @Valid @RequestBody RfqRequest r) {
    return ResponseEntity.ok(service.update(id, r));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PROCUREMENT:DELETE')")
  public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  // ---- Quotes ----

  @PutMapping("/{id}/quotes")
  @PreAuthorize("hasAuthority('PROCUREMENT:EDIT')")
  public ResponseEntity<RfqResponse> saveQuote(@PathVariable("id") Long id, @RequestBody QuoteRequest r) {
    return ResponseEntity.ok(service.saveQuote(id, r));
  }

  @DeleteMapping("/{id}/quotes/{quoteId}")
  @PreAuthorize("hasAuthority('PROCUREMENT:EDIT')")
  public ResponseEntity<RfqResponse> deleteQuote(
      @PathVariable("id") Long id, @PathVariable("quoteId") Long quoteId) {
    return ResponseEntity.ok(service.deleteQuote(id, quoteId));
  }

  // ---- Sending ----

  /**
   * Mint each supplier's quote link and stamp the enquiry sent. Resending is safe: a supplier who
   * already has a link keeps it, so a second send does not break one already sitting in a chat.
   */
  @PutMapping("/{id}/send")
  @PreAuthorize("hasAuthority('PROCUREMENT:EDIT')")
  public ResponseEntity<RfqResponse> send(@PathVariable("id") Long id, @RequestBody(required = false) SendRequest r) {
    return ResponseEntity.ok(service.send(id, r));
  }

  /** Reopen a supplier's link so they can revise. Our decision to make, not theirs. */
  @PutMapping("/{id}/quotes/{quoteId}/unlock")
  @PreAuthorize("hasAuthority('PROCUREMENT:EDIT')")
  public ResponseEntity<RfqResponse> unlock(
      @PathVariable("id") Long id, @PathVariable("quoteId") Long quoteId) {
    return ResponseEntity.ok(service.unlockQuote(id, quoteId));
  }

  // ---- Award ----

  @PutMapping("/{id}/lines/{lineId}/award")
  @PreAuthorize("hasAuthority('PROCUREMENT:EDIT')")
  public ResponseEntity<RfqResponse> award(
      @PathVariable("id") Long id, @PathVariable("lineId") Long lineId, @RequestBody AwardRequest r) {
    return ResponseEntity.ok(service.award(id, lineId, r));
  }
}
