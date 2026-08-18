package com.hitech.erp.approval.api;

import com.hitech.erp.approval.dto.ApprovalDtos.ChainResponse;
import com.hitech.erp.approval.dto.ApprovalDtos.ChainUpdateRequest;
import com.hitech.erp.approval.service.ApprovalChainService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → Multi Level Approval. Configuration only; the running approvals live on each feature's
 * own endpoints (e.g. leave decisions stay under {@code /payroll/leave}).
 */
@RestController
@RequestMapping("/api/v1/approval-chains")
@RequiredArgsConstructor
public class ApprovalChainController {

  private final ApprovalChainService service;

  @GetMapping
  @PreAuthorize("hasAuthority('APPROVAL:VIEW') or hasAuthority('SETTINGS:VIEW')")
  public ResponseEntity<List<ChainResponse>> getAll() {
    return ResponseEntity.ok(service.getAll());
  }

  @GetMapping("/{entityType}")
  @PreAuthorize("hasAuthority('APPROVAL:VIEW') or hasAuthority('SETTINGS:VIEW')")
  public ResponseEntity<ChainResponse> get(@PathVariable("entityType") String entityType) {
    return ResponseEntity.ok(service.get(entityType));
  }

  @PutMapping("/{entityType}")
  @PreAuthorize("hasAuthority('APPROVAL:EDIT') or hasAuthority('SETTINGS:EDIT')")
  public ResponseEntity<ChainResponse> save(
      @PathVariable("entityType") String entityType, @Valid @RequestBody ChainUpdateRequest request) {
    return ResponseEntity.ok(service.save(entityType, request));
  }
}
