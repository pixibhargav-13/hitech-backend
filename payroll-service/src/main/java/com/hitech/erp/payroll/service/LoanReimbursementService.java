package com.hitech.erp.payroll.service;

import com.hitech.erp.common.exception.EntityDeletionNotAllowedException;
import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.payroll.db.LoanEntity;
import com.hitech.erp.payroll.db.LoanRepository;
import com.hitech.erp.payroll.db.ReimbursementEntity;
import com.hitech.erp.payroll.db.ReimbursementRepository;
import com.hitech.erp.payroll.dto.PayrollDtos.LoanRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.LoanResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.ReimbursementCreateRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.ReimbursementDecisionRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.ReimbursementResponse;
import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loans (salary advances repaid via EMI) and Reimbursements (member-paid expenses awaiting
 * approval and payout). Kept in one service because they're small parallel domains — both keyed
 * by member, both read by the payroll run for deductions/additions to net.
 */
@Service
@RequiredArgsConstructor
public class LoanReimbursementService {

  private final LoanRepository loanRepository;
  private final ReimbursementRepository reimbRepository;
  private final AppUserRepository userRepository;

  // ================ Loans ================

  @Transactional(readOnly = true)
  public List<LoanResponse> getLoans() {
    List<LoanEntity> rows = loanRepository.findAllByOrderByCreatedAtDesc();
    Map<Long, String> names = resolveNames(rows.stream().map(LoanEntity::getUserId).toList());
    return rows.stream().map(l -> toLoanResponse(l, names)).toList();
  }

  @Transactional(readOnly = true)
  public List<LoanResponse> getLoansForMember(Long userId) {
    List<LoanEntity> rows = loanRepository.findByUserIdOrderByCreatedAtDesc(userId);
    Map<Long, String> names = resolveNames(List.of(userId));
    return rows.stream().map(l -> toLoanResponse(l, names)).toList();
  }

  @Transactional
  public LoanResponse createLoan(LoanRequest r) {
    LoanEntity e = new LoanEntity();
    applyLoan(e, r);
    if (e.getOutstanding() == null) e.setOutstanding(r.principal());
    return toLoanResponse(loanRepository.save(e), resolveNames(List.of(r.userId())));
  }

  @Transactional
  public LoanResponse updateLoan(Long id, LoanRequest r) {
    LoanEntity e = requireLoan(id);
    applyLoan(e, r);
    return toLoanResponse(loanRepository.save(e), resolveNames(List.of(e.getUserId())));
  }

  @Transactional
  public void deleteLoan(Long id) {
    requireLoan(id);
    loanRepository.deleteById(id);
  }

  private void applyLoan(LoanEntity e, LoanRequest r) {
    e.setUserId(r.userId());
    e.setName(r.name().trim());
    e.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
    e.setPrincipal(r.principal());
    e.setTenureMonths(r.tenureMonths());
    e.setAnnualRate(r.annualRate() == null ? BigDecimal.ZERO : r.annualRate());
    e.setInterestType(r.interestType() == null ? "FLAT" : r.interestType());
    e.setDisbursementDate(LocalDate.parse(r.disbursementDate()));
    e.setStartMonth(r.startMonth());
    e.setEmi(r.emi());
    if (r.outstanding() != null) e.setOutstanding(r.outstanding());
  }

  private LoanEntity requireLoan(Long id) {
    return loanRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Loan not found: " + id));
  }

  private LoanResponse toLoanResponse(LoanEntity e, Map<Long, String> names) {
    return new LoanResponse(
        e.getId(), e.getUserId(), names.getOrDefault(e.getUserId(), ""),
        e.getName(), e.getDescription(), e.getPrincipal(), e.getTenureMonths(),
        e.getAnnualRate(), e.getInterestType(),
        e.getDisbursementDate() == null ? null : e.getDisbursementDate().toString(),
        e.getStartMonth(), e.getEmi(), e.getOutstanding());
  }

  // ================ Reimbursements ================

  @Transactional(readOnly = true)
  public List<ReimbursementResponse> getAll() {
    List<ReimbursementEntity> rows = reimbRepository.findAllByOrderByAppliedAtDesc();
    Map<Long, String> names = resolveNames(collectIds(rows));
    return rows.stream().map(r -> toReimbResponse(r, names)).toList();
  }

  @Transactional(readOnly = true)
  public List<ReimbursementResponse> getForMember(Long userId) {
    List<ReimbursementEntity> rows = reimbRepository.findByUserIdOrderByAppliedAtDesc(userId);
    Map<Long, String> names = resolveNames(collectIds(rows));
    return rows.stream().map(r -> toReimbResponse(r, names)).toList();
  }

  @Transactional
  public ReimbursementResponse create(Long fallbackUserId, ReimbursementCreateRequest r) {
    Long uid = r.userId() != null ? r.userId() : fallbackUserId;
    ReimbursementEntity e = new ReimbursementEntity();
    e.setUserId(uid);
    e.setExpenseType(r.expenseType().trim());
    e.setClaimId(r.claimId() == null || r.claimId().isBlank()
        ? "CLM-" + System.currentTimeMillis()
        : r.claimId().trim());
    e.setExpenseDate(LocalDate.parse(r.expenseDate()));
    e.setAppliedAt(LocalDate.now());
    e.setRequestedAmount(r.requestedAmount());
    e.setStatus("PENDING");
    ReimbursementEntity saved = reimbRepository.save(e);
    return toReimbResponse(saved, resolveNames(collectIds(List.of(saved))));
  }

  @Transactional
  public ReimbursementResponse decide(Long id, Long approverId, ReimbursementDecisionRequest r) {
    ReimbursementEntity e = reimbRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Reimbursement not found: " + id));

    String action = r.action() == null ? "" : r.action().toUpperCase();
    switch (action) {
      case "APPROVE" -> {
        if (!"PENDING".equals(e.getStatus())) throw new EntityDeletionNotAllowedException("Only pending claims can be approved");
        e.setStatus("APPROVED");
        e.setApprovedAt(LocalDate.now());
        e.setApproverId(approverId);
        e.setApprovedAmount(r.approvedAmount() != null ? r.approvedAmount() : e.getRequestedAmount());
      }
      case "REJECT" -> {
        if (!"PENDING".equals(e.getStatus())) throw new EntityDeletionNotAllowedException("Only pending claims can be rejected");
        e.setStatus("REJECTED");
        e.setApprovedAt(LocalDate.now());
        e.setApproverId(approverId);
      }
      case "PAY" -> {
        if (!"APPROVED".equals(e.getStatus())) throw new EntityDeletionNotAllowedException("Only approved claims can be paid");
        e.setStatus("PAID");
        e.setSettlementDate(LocalDate.now());
      }
      default -> throw new EntityDeletionNotAllowedException("Unknown action: " + action);
    }
    return toReimbResponse(reimbRepository.save(e), resolveNames(collectIds(List.of(e))));
  }

  private static List<Long> collectIds(List<ReimbursementEntity> rows) {
    List<Long> ids = new ArrayList<>();
    for (ReimbursementEntity r : rows) {
      ids.add(r.getUserId());
      if (r.getApproverId() != null) ids.add(r.getApproverId());
    }
    return ids;
  }

  private ReimbursementResponse toReimbResponse(ReimbursementEntity r, Map<Long, String> names) {
    return new ReimbursementResponse(
        r.getId(), r.getUserId(), names.getOrDefault(r.getUserId(), ""),
        r.getExpenseType(), r.getClaimId(),
        r.getExpenseDate() == null ? null : r.getExpenseDate().toString(),
        r.getAppliedAt() == null ? null : r.getAppliedAt().toString(),
        r.getApprovedAt() == null ? null : r.getApprovedAt().toString(),
        r.getSettlementDate() == null ? null : r.getSettlementDate().toString(),
        r.getRequestedAmount(), r.getApprovedAmount(),
        r.getApproverId(), r.getApproverId() != null ? names.getOrDefault(r.getApproverId(), "") : null,
        r.getStatus());
  }

  // ---- Common ----

  private Map<Long, String> resolveNames(List<Long> ids) {
    List<Long> distinct = ids.stream().distinct().toList();
    if (distinct.isEmpty()) return Map.of();
    Map<Long, String> out = new HashMap<>();
    for (AppUserEntity u : userRepository.findAllById(distinct)) out.put(u.getId(), u.getFullName());
    return out;
  }
}
