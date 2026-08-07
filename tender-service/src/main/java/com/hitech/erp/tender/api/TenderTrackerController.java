package com.hitech.erp.tender.api;

import com.hitech.erp.tender.dto.TrackerDtos.DocumentRequest;
import com.hitech.erp.tender.dto.TrackerDtos.DocumentResponse;
import com.hitech.erp.tender.dto.TrackerDtos.HardcopyRequest;
import com.hitech.erp.tender.dto.TrackerDtos.HardcopyResponse;
import com.hitech.erp.tender.dto.TrackerDtos.MaterialRequest;
import com.hitech.erp.tender.dto.TrackerDtos.MaterialResponse;
import com.hitech.erp.tender.dto.TrackerDtos.MilestoneRequest;
import com.hitech.erp.tender.dto.TrackerDtos.MilestoneResponse;
import com.hitech.erp.tender.service.TenderTrackerService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Tracker sub-collections for the Tender module: status milestones, documentation, hardcopy, materials. */
@RestController
@RequestMapping("/api/v1/tenders")
@RequiredArgsConstructor
public class TenderTrackerController {

  private final TenderTrackerService service;

  // ---- Milestones ----
  @GetMapping("/milestones")
  @PreAuthorize("hasAuthority('TENDER:VIEW')")
  public ResponseEntity<List<MilestoneResponse>> listMilestones() {
    return ResponseEntity.ok(service.listMilestones());
  }

  @PostMapping("/milestones")
  @PreAuthorize("hasAuthority('TENDER:CREATE')")
  public ResponseEntity<MilestoneResponse> createMilestone(@RequestBody MilestoneRequest r) {
    return ResponseEntity.ok(service.createMilestone(r));
  }

  @PutMapping("/milestones/{id}")
  @PreAuthorize("hasAuthority('TENDER:EDIT')")
  public ResponseEntity<MilestoneResponse> updateMilestone(@PathVariable("id") Long id, @RequestBody MilestoneRequest r) {
    return ResponseEntity.ok(service.updateMilestone(id, r));
  }

  @DeleteMapping("/milestones/{id}")
  @PreAuthorize("hasAuthority('TENDER:DELETE')")
  public ResponseEntity<Void> deleteMilestone(@PathVariable("id") Long id) {
    service.deleteMilestone(id);
    return ResponseEntity.noContent().build();
  }

  // ---- Documents ----
  @GetMapping("/documents")
  @PreAuthorize("hasAuthority('TENDER:VIEW')")
  public ResponseEntity<List<DocumentResponse>> listDocuments() {
    return ResponseEntity.ok(service.listDocuments());
  }

  @PostMapping("/documents")
  @PreAuthorize("hasAuthority('TENDER:CREATE')")
  public ResponseEntity<DocumentResponse> createDocument(@RequestBody DocumentRequest r) {
    return ResponseEntity.ok(service.createDocument(r));
  }

  @PutMapping("/documents/{id}")
  @PreAuthorize("hasAuthority('TENDER:EDIT')")
  public ResponseEntity<DocumentResponse> updateDocument(@PathVariable("id") Long id, @RequestBody DocumentRequest r) {
    return ResponseEntity.ok(service.updateDocument(id, r));
  }

  @DeleteMapping("/documents/{id}")
  @PreAuthorize("hasAuthority('TENDER:DELETE')")
  public ResponseEntity<Void> deleteDocument(@PathVariable("id") Long id) {
    service.deleteDocument(id);
    return ResponseEntity.noContent().build();
  }

  // ---- Hardcopy ----
  @GetMapping("/hardcopy")
  @PreAuthorize("hasAuthority('TENDER:VIEW')")
  public ResponseEntity<List<HardcopyResponse>> listHardcopy() {
    return ResponseEntity.ok(service.listHardcopy());
  }

  @PostMapping("/hardcopy")
  @PreAuthorize("hasAuthority('TENDER:CREATE')")
  public ResponseEntity<HardcopyResponse> createHardcopy(@RequestBody HardcopyRequest r) {
    return ResponseEntity.ok(service.createHardcopy(r));
  }

  @PutMapping("/hardcopy/{id}")
  @PreAuthorize("hasAuthority('TENDER:EDIT')")
  public ResponseEntity<HardcopyResponse> updateHardcopy(@PathVariable("id") Long id, @RequestBody HardcopyRequest r) {
    return ResponseEntity.ok(service.updateHardcopy(id, r));
  }

  @DeleteMapping("/hardcopy/{id}")
  @PreAuthorize("hasAuthority('TENDER:DELETE')")
  public ResponseEntity<Void> deleteHardcopy(@PathVariable("id") Long id) {
    service.deleteHardcopy(id);
    return ResponseEntity.noContent().build();
  }

  // ---- Materials ----
  @GetMapping("/materials")
  @PreAuthorize("hasAuthority('TENDER:VIEW')")
  public ResponseEntity<List<MaterialResponse>> listMaterials() {
    return ResponseEntity.ok(service.listMaterials());
  }

  @PostMapping("/materials")
  @PreAuthorize("hasAuthority('TENDER:CREATE')")
  public ResponseEntity<MaterialResponse> createMaterial(@RequestBody MaterialRequest r) {
    return ResponseEntity.ok(service.createMaterial(r));
  }

  @PutMapping("/materials/{id}")
  @PreAuthorize("hasAuthority('TENDER:EDIT')")
  public ResponseEntity<MaterialResponse> updateMaterial(@PathVariable("id") Long id, @RequestBody MaterialRequest r) {
    return ResponseEntity.ok(service.updateMaterial(id, r));
  }

  @DeleteMapping("/materials/{id}")
  @PreAuthorize("hasAuthority('TENDER:DELETE')")
  public ResponseEntity<Void> deleteMaterial(@PathVariable("id") Long id) {
    service.deleteMaterial(id);
    return ResponseEntity.noContent().build();
  }
}
