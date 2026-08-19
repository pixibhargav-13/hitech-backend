package com.hitech.erp.procurement.api;

import com.hitech.erp.procurement.dto.WorkOrderDtos.ProgressRequest;
import com.hitech.erp.procurement.dto.WorkOrderDtos.SubconBillRequest;
import com.hitech.erp.procurement.dto.WorkOrderDtos.SubconMaterialRequest;
import com.hitech.erp.procurement.dto.WorkOrderDtos.WorkOrderRequest;
import com.hitech.erp.procurement.dto.WorkOrderDtos.WorkOrderResponse;
import com.hitech.erp.procurement.service.WorkOrderService;
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
 * Work orders — subcontracts — and the bills and material issues that hang off them.
 *
 * <p>Bills and material movements get their own routes rather than riding along on the order
 * update. Both are money events with a date and a person behind them, and posting one should not
 * require sending the whole order back — which is also how an edit of the order text would end up
 * silently rewriting a bill.
 */
@RestController
@RequestMapping("/api/v1/procurement/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

  private final WorkOrderService service;

  private static Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) ? u.id() : null;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PROCUREMENT:VIEW')")
  public ResponseEntity<List<WorkOrderResponse>> getAll(
      @RequestParam(name = "projectId", required = false) Long projectId) {
    return ResponseEntity.ok(service.getAll(projectId));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PROCUREMENT:VIEW')")
  public ResponseEntity<WorkOrderResponse> get(@PathVariable("id") Long id) {
    return ResponseEntity.ok(service.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PROCUREMENT:CREATE')")
  public ResponseEntity<WorkOrderResponse> create(@Valid @RequestBody WorkOrderRequest r) {
    return ResponseEntity.ok(service.create(r, currentUserId()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PROCUREMENT:EDIT')")
  public ResponseEntity<WorkOrderResponse> update(@PathVariable("id") Long id, @Valid @RequestBody WorkOrderRequest r) {
    return ResponseEntity.ok(service.update(id, r));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PROCUREMENT:DELETE')")
  public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  // ---- Progress ----

  @PutMapping("/{id}/items/{itemId}/progress")
  @PreAuthorize("hasAuthority('PROCUREMENT:EDIT')")
  public ResponseEntity<WorkOrderResponse> setProgress(
      @PathVariable("id") Long id, @PathVariable("itemId") Long itemId, @RequestBody ProgressRequest r) {
    return ResponseEntity.ok(service.setProgress(id, itemId, r));
  }

  // ---- Running bills ----

  @PutMapping("/{id}/bills")
  @PreAuthorize("hasAuthority('PROCUREMENT:EDIT')")
  public ResponseEntity<WorkOrderResponse> saveBill(@PathVariable("id") Long id, @RequestBody SubconBillRequest r) {
    return ResponseEntity.ok(service.saveBill(id, r, currentUserId()));
  }

  @DeleteMapping("/{id}/bills/{billId}")
  @PreAuthorize("hasAuthority('PROCUREMENT:EDIT')")
  public ResponseEntity<WorkOrderResponse> deleteBill(
      @PathVariable("id") Long id, @PathVariable("billId") Long billId) {
    return ResponseEntity.ok(service.deleteBill(id, billId));
  }

  // ---- Material issued to the subcontractor ----

  @PutMapping("/{id}/materials")
  @PreAuthorize("hasAuthority('PROCUREMENT:EDIT')")
  public ResponseEntity<WorkOrderResponse> saveMaterial(
      @PathVariable("id") Long id, @Valid @RequestBody SubconMaterialRequest r) {
    return ResponseEntity.ok(service.saveMaterial(id, r, currentUserId()));
  }

  @DeleteMapping("/{id}/materials/{materialId}")
  @PreAuthorize("hasAuthority('PROCUREMENT:EDIT')")
  public ResponseEntity<WorkOrderResponse> deleteMaterial(
      @PathVariable("id") Long id, @PathVariable("materialId") Long materialId) {
    return ResponseEntity.ok(service.deleteMaterial(id, materialId));
  }
}
