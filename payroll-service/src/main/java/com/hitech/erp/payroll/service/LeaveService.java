package com.hitech.erp.payroll.service;

import com.hitech.erp.approval.db.ApprovalEntities.Status;
import com.hitech.erp.approval.db.ApprovalEntityType;
import com.hitech.erp.approval.dto.ApprovalDtos.ApprovalStateResponse;
import com.hitech.erp.approval.service.ApprovalService;
import com.hitech.erp.common.exception.EntityDeletionNotAllowedException;
import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.payroll.db.AttendanceEntity;
import com.hitech.erp.payroll.db.AttendanceRepository;
import com.hitech.erp.payroll.db.LeavePolicyEntity;
import com.hitech.erp.payroll.db.LeavePolicyRepository;
import com.hitech.erp.payroll.db.LeaveRequestEntity;
import com.hitech.erp.payroll.db.LeaveRequestRepository;
import com.hitech.erp.payroll.db.LeaveTypeEntity;
import com.hitech.erp.payroll.db.PayrollProfileEntity;
import com.hitech.erp.payroll.db.PayrollProfileRepository;
import com.hitech.erp.payroll.dto.PayrollDtos.LeaveApplyRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.LeaveBalanceResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.LeaveDecisionRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.LeaveRequestResponse;
import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leave applications, approvals, and balance calculation. Approved leave writes PL rows into
 * payroll_attendance for every date in the range so the muster + payroll run pick them up.
 * Balance is a read-time calculation: policy.annualCount − sum(days in approved requests this year).
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

  private final LeaveRequestRepository leaveRepository;
  private final PayrollProfileRepository profileRepository;
  private final LeavePolicyRepository leavePolicyRepository;
  private final AttendanceRepository attendanceRepository;
  private final AppUserRepository userRepository;
  private final ApprovalService approvalService;

  // ---- Reads ----

  @Transactional(readOnly = true)
  public List<LeaveRequestResponse> getForMember(Long userId) {
    List<LeaveRequestEntity> rows = leaveRepository.findByUserIdOrderByCreatedAtDesc(userId);
    return withApproval(rows, null);
  }

  @Transactional(readOnly = true)
  public List<LeaveRequestResponse> getPending() {
    List<LeaveRequestEntity> rows = leaveRepository.findByStatusOrderByCreatedAtDesc("PENDING");
    return withApproval(rows, null);
  }

  /**
   * Every leave request a manager should see — pending and decided together.
   *
   * <p>The old screen split "approval queue" and "past requests" into separate pages, which meant
   * you couldn't see a request's history next to the decision you were about to make. One list,
   * filtered client-side, with each row carrying its own chain state and whether the caller can act.
   */
  @Transactional(readOnly = true)
  public List<LeaveRequestResponse> getAllForApprover(AuthenticatedUser actor) {
    return withApproval(leaveRepository.findAllByOrderByCreatedAtDesc(), actor);
  }

  /**
   * Attaches chain state to a page of rows in one query rather than one per row, and computes
   * {@code canActNow} per row so the UI never has to guess whose turn it is.
   */
  private List<LeaveRequestResponse> withApproval(List<LeaveRequestEntity> rows, AuthenticatedUser actor) {
    if (rows.isEmpty()) return List.of();
    Map<Long, ApprovalStateResponse> states = approvalService.statesFor(
        ApprovalEntityType.LEAVE_APPLICATION,
        rows.stream().map(LeaveRequestEntity::getId).toList(),
        actor);
    return rows.stream().map(e -> toResponse(e, states.get(e.getId()))).toList();
  }

  /** How many days of each leave type a member has used vs their assigned Leave Policy's grant. */
  @Transactional(readOnly = true)
  public List<LeaveBalanceResponse> getBalance(Long userId) {
    PayrollProfileEntity profile = profileRepository.findByUserId(userId).orElse(null);
    if (profile == null || profile.getLeavePolicyId() == null) return List.of();

    LeavePolicyEntity policy = leavePolicyRepository.findById(profile.getLeavePolicyId()).orElse(null);
    if (policy == null) return List.of();

    // Sum approved leave days per type for the current year (matches the policy cycle for YEARLY).
    LocalDate yearStart = LocalDate.of(LocalDate.now().getYear(), 1, 1);
    LocalDate yearEnd = LocalDate.of(LocalDate.now().getYear(), 12, 31);
    List<LeaveRequestEntity> taken = leaveRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .filter(r -> "APPROVED".equals(r.getStatus()))
        .filter(r -> !r.getFromDate().isAfter(yearEnd) && !r.getToDate().isBefore(yearStart))
        .toList();

    Map<String, BigDecimal> takenByType = new HashMap<>();
    for (LeaveRequestEntity r : taken) {
      takenByType.merge(r.getLeaveTypeName(), r.getDays(), BigDecimal::add);
    }

    List<LeaveBalanceResponse> out = new ArrayList<>();
    for (LeaveTypeEntity t : policy.getTypes()) {
      BigDecimal used = takenByType.getOrDefault(t.getName(), BigDecimal.ZERO);
      BigDecimal remaining = new BigDecimal(t.getAnnualCount()).subtract(used).max(BigDecimal.ZERO);
      out.add(new LeaveBalanceResponse(t.getName(), t.getAnnualCount(), used, remaining, t.isPaid()));
    }
    return out;
  }

  // ---- Writes ----

  @Transactional
  public LeaveRequestResponse apply(AuthenticatedUser requester, LeaveApplyRequest r) {
    Long userId = requester.id();
    LocalDate from = LocalDate.parse(r.fromDate());
    LocalDate to = LocalDate.parse(r.toDate());
    if (to.isBefore(from)) {
      throw new EntityDeletionNotAllowedException("End date must be on or after the start date");
    }
    long days = ChronoUnit.DAYS.between(from, to) + 1; // inclusive

    LeaveRequestEntity e = new LeaveRequestEntity();
    e.setUserId(userId);
    e.setLeaveTypeName(r.leaveTypeName().trim());
    e.setFromDate(from);
    e.setToDate(to);
    e.setDays(new BigDecimal(days).setScale(1, RoundingMode.HALF_UP));
    e.setReason(r.reason() == null || r.reason().isBlank() ? null : r.reason().trim());
    e.setStatus("PENDING");

    LeaveRequestEntity saved = leaveRepository.save(e);
    // Raise the approval chain if one is published for leave. When none is, submit() returns empty
    // and the request behaves exactly as it did before — a single manager decision closes it.
    approvalService.submit(ApprovalEntityType.LEAVE_APPLICATION, saved.getId(), requester);
    // Return the chain with the response — the applicant should see who it went to, not a bare
    // "PENDING" that tells them nothing about where their request now sits.
    return toResponse(saved, approvalService.state(ApprovalEntityType.LEAVE_APPLICATION, saved.getId(), requester));
  }

  /**
   * Record one decision on a leave request.
   *
   * <p>With a published chain this is a <em>rung</em>, not a verdict: the leave row only moves to
   * APPROVED once the last level has signed off. A rejection at any level ends it immediately. The
   * approval framework enforces who may act and writes the audit trail.
   */
  @Transactional
  public LeaveRequestResponse decide(Long requestId, AuthenticatedUser actor, LeaveDecisionRequest r) {
    LeaveRequestEntity e = leaveRepository.findById(requestId)
        .orElseThrow(() -> new EntityNotFoundException("Leave request not found: " + requestId));
    if (!"PENDING".equals(e.getStatus())) {
      throw new EntityDeletionNotAllowedException("This request is already " + e.getStatus().toLowerCase());
    }

    boolean approve = "APPROVE".equalsIgnoreCase(r.action());
    String note = r.note() == null || r.note().isBlank() ? null : r.note().trim();

    ApprovalStateResponse chain = approvalService.state(ApprovalEntityType.LEAVE_APPLICATION, requestId, actor);
    if (chain != null) {
      Status outcome = approvalService.decide(
          ApprovalEntityType.LEAVE_APPLICATION, requestId, actor, approve, note);
      // Always stamp who acted last, so the list still shows a decision-maker mid-chain.
      e.setApproverId(actor.id());
      e.setApprovedAt(LocalDateTime.now());
      e.setDecisionNote(note);
      if (outcome == Status.PENDING) {
        // More levels to go — the leave itself stays pending, but hand back the advanced ladder.
        return toResponse(
            leaveRepository.save(e),
            approvalService.state(ApprovalEntityType.LEAVE_APPLICATION, requestId, actor));
      }
      e.setStatus(outcome == Status.APPROVED ? "APPROVED" : "REJECTED");
      approve = outcome == Status.APPROVED;
    } else {
      // No chain for leave: the old single-decision behaviour, which still needs the blanket
      // payroll-approve right since nobody has been named as an approver for this request.
      if (actor.permissions() == null || !actor.permissions().contains("PAYROLL:APPROVE")) {
        throw new org.springframework.security.access.AccessDeniedException(
            "You don't have permission to decide leave requests.");
      }
      e.setStatus(approve ? "APPROVED" : "REJECTED");
      e.setApproverId(actor.id());
      e.setApprovedAt(LocalDateTime.now());
      e.setDecisionNote(note);
    }

    // On approve, write PL attendance rows for the whole range (only where none exists yet).
    if (approve) {
      for (LocalDate d = e.getFromDate(); !d.isAfter(e.getToDate()); d = d.plusDays(1)) {
        LocalDate day = d;
        AttendanceEntity a = attendanceRepository.findByUserIdAndDate(e.getUserId(), day).orElseGet(() -> {
          AttendanceEntity fresh = new AttendanceEntity();
          fresh.setUserId(e.getUserId());
          fresh.setDate(day);
          return fresh;
        });
        // Don't clobber an already-P/HD record — leave overrides only NM/A.
        if ("NM".equals(a.getCode()) || "A".equals(a.getCode()) || a.getId() == null) {
          a.setCode("PL");
          attendanceRepository.save(a);
        }
      }
    }

    return toResponse(
        leaveRepository.save(e),
        approvalService.state(ApprovalEntityType.LEAVE_APPLICATION, requestId, actor));
  }

  /** Member cancels their own pending request. */
  @Transactional
  public LeaveRequestResponse cancel(Long requestId, AuthenticatedUser actor) {
    Long userId = actor.id();
    LeaveRequestEntity e = leaveRepository.findById(requestId)
        .orElseThrow(() -> new EntityNotFoundException("Leave request not found: " + requestId));
    if (!e.getUserId().equals(userId)) {
      throw new EntityDeletionNotAllowedException("You can only cancel your own leave requests");
    }
    if (!"PENDING".equals(e.getStatus())) {
      throw new EntityDeletionNotAllowedException("Only pending requests can be cancelled");
    }
    e.setStatus("CANCELLED");
    // Withdraw the chain too, so it stops appearing in anyone's approval queue.
    approvalService.cancel(ApprovalEntityType.LEAVE_APPLICATION, requestId, actor);
    return toResponse(
        leaveRepository.save(e),
        approvalService.state(ApprovalEntityType.LEAVE_APPLICATION, requestId, actor));
  }

  // ---- Helpers ----

  private LeaveRequestResponse toResponse(LeaveRequestEntity e) {
    return toResponse(e, null);
  }

  private LeaveRequestResponse toResponse(LeaveRequestEntity e, ApprovalStateResponse approval) {
    Map<Long, String> names = new HashMap<>();
    List<Long> ids = new ArrayList<>();
    ids.add(e.getUserId());
    if (e.getApproverId() != null) ids.add(e.getApproverId());
    for (AppUserEntity u : userRepository.findAllById(ids)) names.put(u.getId(), u.getFullName());

    return new LeaveRequestResponse(
        e.getId(), e.getUserId(), names.getOrDefault(e.getUserId(), ""),
        e.getLeaveTypeName(), e.getFromDate().toString(), e.getToDate().toString(),
        e.getDays(), e.getReason(), e.getStatus(),
        e.getApproverId(), e.getApproverId() != null ? names.getOrDefault(e.getApproverId(), "") : null,
        e.getApprovedAt() != null ? e.getApprovedAt().toString() : null,
        e.getDecisionNote(),
        e.getCreatedAt() != null ? e.getCreatedAt().toString() : null,
        approval,
        approval != null && approval.canActNow());
  }
}
