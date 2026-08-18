package com.hitech.erp.tender.api;

import com.hitech.erp.tender.dto.TenderDtos.StageChangeRequest;
import com.hitech.erp.tender.dto.TenderDtos.TenderPageResponse;
import com.hitech.erp.tender.dto.TenderDtos.TenderRequest;
import com.hitech.erp.tender.dto.TenderDtos.TenderResponse;
import com.hitech.erp.tender.dto.TenderDtos.TenderSummary;
import com.hitech.erp.tender.service.TenderService;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** REST surface for the Tender bidding pipeline. Everything is gated behind TENDER:* permissions. */
@RestController
@RequestMapping("/api/v1/tenders")
@RequiredArgsConstructor
public class TenderController {

  private final TenderService service;

  private static Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) ? u.id() : null;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('TENDER:VIEW')")
  public ResponseEntity<TenderPageResponse> getTenders(
      @RequestParam(name = "page", required = false, defaultValue = "0") int page,
      @RequestParam(name = "size", required = false, defaultValue = "50") int size,
      @RequestParam(name = "stage", required = false) String stage,
      @RequestParam(name = "source", required = false) String source,
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "projectId", required = false) Long projectId) {
    return ResponseEntity.ok(service.getTenders(page, size, stage, source, q, projectId));
  }

  @GetMapping("/summary")
  @PreAuthorize("hasAuthority('TENDER:VIEW')")
  public ResponseEntity<TenderSummary> summary() {
    return ResponseEntity.ok(service.summary());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('TENDER:VIEW')")
  public ResponseEntity<TenderResponse> getTender(@PathVariable("id") Long id) {
    return ResponseEntity.ok(service.getTender(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('TENDER:CREATE')")
  public ResponseEntity<TenderResponse> createTender(@Valid @RequestBody TenderRequest request) {
    return ResponseEntity.ok(service.create(request, currentUserId()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('TENDER:EDIT')")
  public ResponseEntity<TenderResponse> updateTender(
      @PathVariable("id") Long id, @Valid @RequestBody TenderRequest request) {
    return ResponseEntity.ok(service.update(id, request));
  }

  @PatchMapping("/{id}/stage")
  @PreAuthorize("hasAuthority('TENDER:EDIT')")
  public ResponseEntity<TenderResponse> changeStage(
      @PathVariable("id") Long id, @RequestBody StageChangeRequest request) {
    return ResponseEntity.ok(service.changeStage(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('TENDER:DELETE')")
  public ResponseEntity<Void> deleteTender(@PathVariable("id") Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
