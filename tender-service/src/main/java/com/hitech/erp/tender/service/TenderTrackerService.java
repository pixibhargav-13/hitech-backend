package com.hitech.erp.tender.service;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.tender.db.TenderDocumentEntity;
import com.hitech.erp.tender.db.TenderDocumentRepository;
import com.hitech.erp.tender.db.TenderHardcopyEntity;
import com.hitech.erp.tender.db.TenderHardcopyRepository;
import com.hitech.erp.tender.db.TenderMaterialEntity;
import com.hitech.erp.tender.db.TenderMaterialRepository;
import com.hitech.erp.tender.db.TenderMilestoneEntity;
import com.hitech.erp.tender.db.TenderMilestoneRepository;
import com.hitech.erp.tender.dto.TrackerDtos.DocumentRequest;
import com.hitech.erp.tender.dto.TrackerDtos.DocumentResponse;
import com.hitech.erp.tender.dto.TrackerDtos.HardcopyRequest;
import com.hitech.erp.tender.dto.TrackerDtos.HardcopyResponse;
import com.hitech.erp.tender.dto.TrackerDtos.MaterialRequest;
import com.hitech.erp.tender.dto.TrackerDtos.MaterialResponse;
import com.hitech.erp.tender.dto.TrackerDtos.MilestoneRequest;
import com.hitech.erp.tender.dto.TrackerDtos.MilestoneResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD for the tender tracker sub-collections: status milestones, documentation, hardcopy, materials. */
@Service
@RequiredArgsConstructor
public class TenderTrackerService {

  private final TenderMilestoneRepository milestoneRepo;
  private final TenderDocumentRepository documentRepo;
  private final TenderHardcopyRepository hardcopyRepo;
  private final TenderMaterialRepository materialRepo;

  // ================= Milestones =================

  @Transactional(readOnly = true)
  public List<MilestoneResponse> listMilestones() {
    return milestoneRepo.findAll().stream().map(TenderTrackerService::toMilestone).toList();
  }

  @Transactional
  public MilestoneResponse createMilestone(MilestoneRequest r) {
    TenderMilestoneEntity e = new TenderMilestoneEntity();
    applyMilestone(e, r);
    return toMilestone(milestoneRepo.save(e));
  }

  @Transactional
  public MilestoneResponse updateMilestone(Long id, MilestoneRequest r) {
    TenderMilestoneEntity e =
        milestoneRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Milestone not found: " + id));
    applyMilestone(e, r);
    return toMilestone(milestoneRepo.save(e));
  }

  @Transactional
  public void deleteMilestone(Long id) {
    if (!milestoneRepo.existsById(id)) throw new EntityNotFoundException("Milestone not found: " + id);
    milestoneRepo.deleteById(id);
  }

  private static void applyMilestone(TenderMilestoneEntity e, MilestoneRequest r) {
    if (r.tenderRef() != null) e.setTenderRef(r.tenderRef());
    if (r.nameOfWork() != null) e.setNameOfWork(r.nameOfWork());
    if (r.workStartDate() != null) e.setWorkStartDate(r.workStartDate());
    if (r.progress() != null) e.setProgress(r.progress());
    if (r.stepsJson() != null) e.setStepsJson(r.stepsJson());
  }

  private static MilestoneResponse toMilestone(TenderMilestoneEntity e) {
    return new MilestoneResponse(
        e.getId(), e.getTenderRef(), e.getNameOfWork(), e.getWorkStartDate(), e.getProgress(), e.getStepsJson());
  }

  // ================= Documents =================

  @Transactional(readOnly = true)
  public List<DocumentResponse> listDocuments() {
    return documentRepo.findAll().stream().map(TenderTrackerService::toDocument).toList();
  }

  @Transactional
  public DocumentResponse createDocument(DocumentRequest r) {
    TenderDocumentEntity e = new TenderDocumentEntity();
    applyDocument(e, r);
    return toDocument(documentRepo.save(e));
  }

  @Transactional
  public DocumentResponse updateDocument(Long id, DocumentRequest r) {
    TenderDocumentEntity e =
        documentRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Document not found: " + id));
    applyDocument(e, r);
    return toDocument(documentRepo.save(e));
  }

  @Transactional
  public void deleteDocument(Long id) {
    if (!documentRepo.existsById(id)) throw new EntityNotFoundException("Document not found: " + id);
    documentRepo.deleteById(id);
  }

  private static void applyDocument(TenderDocumentEntity e, DocumentRequest r) {
    if (r.tenderRef() != null) e.setTenderRef(r.tenderRef());
    if (r.nameOfWork() != null) e.setNameOfWork(r.nameOfWork());
    if (r.progress() != null) e.setProgress(r.progress());
    if (r.viewDocuments() != null) e.setViewDocuments(r.viewDocuments());
    if (r.pairsJson() != null) e.setPairsJson(r.pairsJson());
    if (r.raBillsJson() != null) e.setRaBillsJson(r.raBillsJson());
  }

  private static DocumentResponse toDocument(TenderDocumentEntity e) {
    return new DocumentResponse(
        e.getId(),
        e.getTenderRef(),
        e.getNameOfWork(),
        e.getProgress(),
        e.getViewDocuments(),
        e.getPairsJson(),
        e.getRaBillsJson());
  }

  // ================= Hardcopy =================

  @Transactional(readOnly = true)
  public List<HardcopyResponse> listHardcopy() {
    return hardcopyRepo.findAll().stream().map(TenderTrackerService::toHardcopy).toList();
  }

  @Transactional
  public HardcopyResponse createHardcopy(HardcopyRequest r) {
    TenderHardcopyEntity e = new TenderHardcopyEntity();
    applyHardcopy(e, r);
    return toHardcopy(hardcopyRepo.save(e));
  }

  @Transactional
  public HardcopyResponse updateHardcopy(Long id, HardcopyRequest r) {
    TenderHardcopyEntity e =
        hardcopyRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Hardcopy not found: " + id));
    applyHardcopy(e, r);
    return toHardcopy(hardcopyRepo.save(e));
  }

  @Transactional
  public void deleteHardcopy(Long id) {
    if (!hardcopyRepo.existsById(id)) throw new EntityNotFoundException("Hardcopy not found: " + id);
    hardcopyRepo.deleteById(id);
  }

  private static void applyHardcopy(TenderHardcopyEntity e, HardcopyRequest r) {
    if (r.date() != null) e.setDate(r.date());
    if (r.nameOfWork() != null) e.setNameOfWork(r.nameOfWork());
    if (r.tenderRef() != null) e.setTenderRef(r.tenderRef());
    if (r.documentList() != null) e.setDocumentList(r.documentList());
    if (r.packedBy() != null) e.setPackedBy(r.packedBy());
    if (r.dispatchBy() != null) e.setDispatchBy(r.dispatchBy());
    if (r.trackingNo() != null) e.setTrackingNo(r.trackingNo());
    if (r.arrived() != null) e.setArrived(r.arrived());
    if (r.arrivedDate() != null) e.setArrivedDate(r.arrivedDate());
    if (r.remarks() != null) e.setRemarks(r.remarks());
  }

  private static HardcopyResponse toHardcopy(TenderHardcopyEntity e) {
    return new HardcopyResponse(
        e.getId(),
        e.getDate(),
        e.getNameOfWork(),
        e.getTenderRef(),
        e.getDocumentList(),
        e.getPackedBy(),
        e.getDispatchBy(),
        e.getTrackingNo(),
        e.getArrived(),
        e.getArrivedDate(),
        e.getRemarks());
  }

  // ================= Materials =================

  @Transactional(readOnly = true)
  public List<MaterialResponse> listMaterials() {
    return materialRepo.findAll().stream().map(TenderTrackerService::toMaterial).toList();
  }

  @Transactional
  public MaterialResponse createMaterial(MaterialRequest r) {
    TenderMaterialEntity e = new TenderMaterialEntity();
    applyMaterial(e, r);
    return toMaterial(materialRepo.save(e));
  }

  @Transactional
  public MaterialResponse updateMaterial(Long id, MaterialRequest r) {
    TenderMaterialEntity e =
        materialRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Material not found: " + id));
    applyMaterial(e, r);
    return toMaterial(materialRepo.save(e));
  }

  @Transactional
  public void deleteMaterial(Long id) {
    if (!materialRepo.existsById(id)) throw new EntityNotFoundException("Material not found: " + id);
    materialRepo.deleteById(id);
  }

  private static void applyMaterial(TenderMaterialEntity e, MaterialRequest r) {
    if (r.party() != null) e.setParty(r.party());
    if (r.manufacturerType() != null) e.setManufacturerType(r.manufacturerType());
    if (r.make() != null) e.setMake(r.make());
    if (r.location() != null) e.setLocation(r.location());
    if (r.contact() != null) e.setContact(r.contact());
    if (r.email() != null) e.setEmail(r.email());
  }

  private static MaterialResponse toMaterial(TenderMaterialEntity e) {
    return new MaterialResponse(
        e.getId(),
        e.getParty(),
        e.getManufacturerType(),
        e.getMake(),
        e.getLocation(),
        e.getContact(),
        e.getEmail());
  }
}
