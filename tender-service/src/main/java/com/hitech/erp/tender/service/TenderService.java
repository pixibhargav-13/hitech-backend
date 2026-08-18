package com.hitech.erp.tender.service;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.tender.db.TenderEntity;
import com.hitech.erp.tender.db.TenderRepository;
import com.hitech.erp.tender.db.TenderSource;
import com.hitech.erp.tender.db.TenderStage;
import com.hitech.erp.tender.db.TenderStatus;
import com.hitech.erp.tender.dto.TenderDtos.StageChangeRequest;
import com.hitech.erp.tender.dto.TenderDtos.StageCount;
import com.hitech.erp.tender.dto.TenderDtos.TenderPageResponse;
import com.hitech.erp.tender.dto.TenderDtos.TenderRequest;
import com.hitech.erp.tender.dto.TenderDtos.TenderResponse;
import com.hitech.erp.tender.dto.TenderDtos.TenderSummary;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The tender bidding pipeline. Records move Sorting → Research → Applied → Won/Lost; every field is
 * optional so a tender can be captured early and completed later. EMD figures are derived on the fly
 * for the dashboard rather than stored, so they can never drift from the underlying records.
 */
@Service
@RequiredArgsConstructor
public class TenderService {

  private final TenderRepository repository;

  @Transactional(readOnly = true)
  public TenderPageResponse getTenders(
      int page, int size, String stage, String source, String q, Long projectId) {
    Specification<TenderEntity> spec = buildSpec(stage, source, q, projectId);
    Page<TenderEntity> result =
        repository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    return new TenderPageResponse(
        result.getContent().stream().map(this::toResponse).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public TenderResponse getTender(Long id) {
    return toResponse(require(id));
  }

  @Transactional
  public TenderResponse create(TenderRequest r, Long userId) {
    TenderEntity t = new TenderEntity();
    // Sensible defaults for a fresh record; the request can override any of them.
    t.setSource(TenderSource.PORTAL);
    t.setStage(TenderStage.SORTING);
    t.setCreatedBy(userId);
    apply(t, r);
    return toResponse(repository.save(t));
  }

  @Transactional
  public TenderResponse update(Long id, TenderRequest r) {
    TenderEntity t = require(id);
    apply(t, r);
    return toResponse(repository.save(t));
  }

  /** Move a tender between pipeline stages, optionally stamping the applied-stage status. */
  @Transactional
  public TenderResponse changeStage(Long id, StageChangeRequest r) {
    TenderEntity t = require(id);
    if (!StringUtils.hasText(r.stage())) {
      throw new IllegalArgumentException("A target stage is required.");
    }
    t.setStage(parseStage(r.stage()));
    if (StringUtils.hasText(r.status())) {
      t.setStatus(parseStatus(r.status()));
    }
    return toResponse(repository.save(t));
  }

  @Transactional
  public void delete(Long id) {
    repository.delete(require(id));
  }

  @Transactional(readOnly = true)
  public TenderSummary summary() {
    List<TenderEntity> all = repository.findAll();
    List<StageCount> byStage = new ArrayList<>();
    for (TenderStage s : TenderStage.values()) {
      long c = all.stream().filter(t -> t.getStage() == s).count();
      byStage.add(new StageCount(s.name(), c));
    }
    // Blocked = paid EMD still tied up in a live or won bid. Recoverable = money against a lost
    // tender that no release has been recorded for (the workbook never tracked releases).
    double blocked =
        all.stream()
            .filter(t -> t.getEmd() != null && "PAID".equalsIgnoreCase(t.getEmdState()) && t.getStage() != TenderStage.LOST)
            .mapToDouble(TenderEntity::getEmd)
            .sum();
    double recoverable =
        all.stream()
            .filter(
                t ->
                    t.getEmd() != null
                        && t.getStage() == TenderStage.LOST
                        && !"RELEASED".equalsIgnoreCase(t.getEmdState()))
            .mapToDouble(TenderEntity::getEmd)
            .sum();
    return new TenderSummary(all.size(), byStage, blocked, recoverable);
  }

  /**
   * The tenders a project came from — the Project workspace's Tender tab. Closes the handoff the
   * pipeline already implies: winning a bid produces a project, but until now the finished project
   * had no way back to the bid that created it (EMD, contract value, submission dates).
   */
  @Transactional(readOnly = true)
  public List<TenderResponse> byProject(Long projectId) {
    return repository.findByProjectIdOrderByIdDesc(projectId).stream().map(this::toResponse).toList();
  }

  // ---------------- internals ----------------

  private TenderEntity require(Long id) {
    return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Tender not found: " + id));
  }

  private Specification<TenderEntity> buildSpec(String stage, String source, String q, Long projectId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (projectId != null) {
        predicates.add(cb.equal(root.get("projectId"), projectId));
      }
      if (StringUtils.hasText(stage)) {
        predicates.add(cb.equal(root.get("stage"), parseStage(stage)));
      }
      if (StringUtils.hasText(source)) {
        predicates.add(cb.equal(root.get("source"), parseSource(source)));
      }
      if (StringUtils.hasText(q)) {
        String like = "%" + q.toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("nameOfWork")), like),
                cb.like(cb.lower(root.get("department")), like),
                cb.like(cb.lower(root.get("tenderId")), like)));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /** Copy non-null request fields onto the entity — nulls leave the existing value untouched. */
  private void apply(TenderEntity t, TenderRequest r) {
    if (r.source() != null) t.setSource(parseSource(r.source()));
    if (r.stage() != null) t.setStage(parseStage(r.stage()));
    if (r.status() != null) t.setStatus(r.status().isBlank() ? null : parseStatus(r.status()));
    if (r.statusLabel() != null) t.setStatusLabel(r.statusLabel());
    if (r.department() != null) t.setDepartment(r.department());
    if (r.tenderId() != null) t.setTenderId(r.tenderId());
    if (r.nameOfWork() != null) t.setNameOfWork(r.nameOfWork());
    if (r.location() != null) t.setLocation(r.location());
    if (r.officeAddress() != null) t.setOfficeAddress(r.officeAddress());
    if (r.estimatedCost() != null) t.setEstimatedCost(r.estimatedCost());
    if (r.contractValue() != null) t.setContractValue(r.contractValue());
    if (r.variancePct() != null) t.setVariancePct(r.variancePct());
    if (r.duration() != null) t.setDuration(r.duration());
    if (r.durationMonths() != null) t.setDurationMonths(r.durationMonths());
    if (r.deadline() != null) t.setDeadline(r.deadline());
    if (r.nextFollowUp() != null) t.setNextFollowUp(r.nextFollowUp());
    if (r.dueDate() != null) t.setDueDate(r.dueDate());
    if (r.submissionDate() != null) t.setSubmissionDate(r.submissionDate());
    if (r.hardcopyDue() != null) t.setHardcopyDue(r.hardcopyDue());
    if (r.techOpen() != null) t.setTechOpen(r.techOpen());
    if (r.priceOpen() != null) t.setPriceOpen(r.priceOpen());
    if (r.validity() != null) t.setValidity(r.validity());
    if (r.validityDays() != null) t.setValidityDays(r.validityDays());
    if (r.dlp() != null) t.setDlp(r.dlp());
    if (r.openingDate() != null) t.setOpeningDate(r.openingDate());
    if (r.preBidDate() != null) t.setPreBidDate(r.preBidDate());
    if (r.fee() != null) t.setFee(r.fee());
    if (r.emd() != null) t.setEmd(r.emd());
    if (r.emdMode() != null) t.setEmdMode(r.emdMode());
    if (r.emdState() != null) t.setEmdState(r.emdState());
    if (r.emdPaidOn() != null) t.setEmdPaidOn(r.emdPaidOn());
    if (r.emdReleasedOn() != null) t.setEmdReleasedOn(r.emdReleasedOn());
    if (r.emdInstrumentNo() != null) t.setEmdInstrumentNo(r.emdInstrumentNo());
    if (r.emdExpiry() != null) t.setEmdExpiry(r.emdExpiry());
    if (r.pqCriteria() != null) t.setPqCriteria(r.pqCriteria());
    if (r.classReq() != null) t.setClassReq(r.classReq());
    if (r.gst() != null) t.setGst(r.gst());
    if (r.labTest() != null) t.setLabTest(r.labTest());
    if (r.priceEscalation() != null) t.setPriceEscalation(r.priceEscalation());
    if (r.depositDetails() != null) t.setDepositDetails(r.depositDetails());
    if (r.preBidInfo() != null) t.setPreBidInfo(r.preBidInfo());
    if (r.firm() != null) t.setFirm(r.firm());
    if (r.securityType() != null) t.setSecurityType(r.securityType());
    if (r.securityAmount() != null) t.setSecurityAmount(r.securityAmount());
    if (r.additionalSecurityType() != null) t.setAdditionalSecurityType(r.additionalSecurityType());
    if (r.additionalSecurityAmount() != null) t.setAdditionalSecurityAmount(r.additionalSecurityAmount());
    if (r.bgCharges() != null) t.setBgCharges(r.bgCharges());
    if (r.securityReleasedOn() != null) t.setSecurityReleasedOn(r.securityReleasedOn());
    if (r.receivedStatus() != null) t.setReceivedStatus(r.receivedStatus());
    if (r.dateOfReceived() != null) t.setDateOfReceived(r.dateOfReceived());
    if (r.priceBidStage() != null) t.setPriceBidStage(r.priceBidStage());
    if (r.lossReason() != null) t.setLossReason(r.lossReason());
    if (r.lossReasonLabel() != null) t.setLossReasonLabel(r.lossReasonLabel());
    if (r.lossNote() != null) t.setLossNote(r.lossNote());
    if (r.l1Bidder() != null) t.setL1Bidder(r.l1Bidder());
    if (r.l1Value() != null) t.setL1Value(r.l1Value());
    if (r.ourRank() != null) t.setOurRank(r.ourRank());
    if (r.gemCategory() != null) t.setGemCategory(r.gemCategory());
    if (r.msmeRelaxation() != null) t.setMsmeRelaxation(r.msmeRelaxation());
    if (r.experienceTurnover() != null) t.setExperienceTurnover(r.experienceTurnover());
    if (r.eligibilityStatus() != null) t.setEligibilityStatus(r.eligibilityStatus());
    if (r.priority() != null) t.setPriority(r.priority());
    if (r.viewDocuments() != null) t.setViewDocuments(r.viewDocuments());
    if (r.remarks() != null) t.setRemarks(r.remarks());
    if (r.customFields() != null) t.setCustomFields(r.customFields());
    if (r.projectId() != null) t.setProjectId(r.projectId());
  }

  private TenderResponse toResponse(TenderEntity t) {
    return new TenderResponse(
        t.getId(),
        t.getSource() == null ? null : t.getSource().name(),
        t.getStage() == null ? null : t.getStage().name(),
        t.getStatus() == null ? null : t.getStatus().name(),
        t.getStatusLabel(),
        t.getDepartment(),
        t.getTenderId(),
        t.getNameOfWork(),
        t.getLocation(),
        t.getOfficeAddress(),
        t.getEstimatedCost(),
        t.getContractValue(),
        t.getVariancePct(),
        t.getDuration(),
        t.getDurationMonths(),
        t.getDeadline(),
        t.getNextFollowUp(),
        t.getDueDate(),
        t.getSubmissionDate(),
        t.getHardcopyDue(),
        t.getTechOpen(),
        t.getPriceOpen(),
        t.getValidity(),
        t.getValidityDays(),
        t.getDlp(),
        t.getOpeningDate(),
        t.getPreBidDate(),
        t.getFee(),
        t.getEmd(),
        t.getEmdMode(),
        t.getEmdState(),
        t.getEmdPaidOn(),
        t.getEmdReleasedOn(),
        t.getEmdInstrumentNo(),
        t.getEmdExpiry(),
        t.getPqCriteria(),
        t.getClassReq(),
        t.getGst(),
        t.getLabTest(),
        t.getPriceEscalation(),
        t.getDepositDetails(),
        t.getPreBidInfo(),
        t.getFirm(),
        t.getSecurityType(),
        t.getSecurityAmount(),
        t.getAdditionalSecurityType(),
        t.getAdditionalSecurityAmount(),
        t.getBgCharges(),
        t.getSecurityReleasedOn(),
        t.getReceivedStatus(),
        t.getDateOfReceived(),
        t.getPriceBidStage(),
        t.getLossReason(),
        t.getLossReasonLabel(),
        t.getLossNote(),
        t.getL1Bidder(),
        t.getL1Value(),
        t.getOurRank(),
        t.getGemCategory(),
        t.getMsmeRelaxation(),
        t.getExperienceTurnover(),
        t.getEligibilityStatus(),
        t.getPriority(),
        t.getViewDocuments(),
        t.getRemarks(),
        t.getCustomFields(),
        t.getProjectId(),
        t.getCreatedAt() == null ? null : t.getCreatedAt().toString(),
        t.getUpdatedAt() == null ? null : t.getUpdatedAt().toString());
  }

  private TenderStage parseStage(String value) {
    try {
      return TenderStage.valueOf(norm(value));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid tender stage: " + value);
    }
  }

  private TenderStatus parseStatus(String value) {
    try {
      return TenderStatus.valueOf(norm(value));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid tender status: " + value);
    }
  }

  private TenderSource parseSource(String value) {
    try {
      return TenderSource.valueOf(norm(value));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid tender source: " + value);
    }
  }

  private static String norm(String value) {
    return value.trim().toUpperCase().replace(' ', '_').replace('-', '_');
  }
}
