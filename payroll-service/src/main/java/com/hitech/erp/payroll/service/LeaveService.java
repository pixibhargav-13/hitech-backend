package com.hitech.erp.payroll.service;

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

  // ---- Reads ----

  @Transactional(readOnly = true)
  public List<LeaveRequestResponse> getForMember(Long userId) {
    List<LeaveRequestEntity> rows = leaveRepository.findByUserIdOrderByCreatedAtDesc(userId);
    return rows.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<LeaveRequestResponse> getPending() {
    List<LeaveRequestEntity> rows = leaveRepository.findByStatusOrderByCreatedAtDesc("PENDING");
    return rows.stream().map(this::toResponse).toList();
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
  public LeaveRequestResponse apply(Long userId, LeaveApplyRequest r) {
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

    return toResponse(leaveRepository.save(e));
  }

  @Transactional
  public LeaveRequestResponse decide(Long requestId, Long approverId, LeaveDecisionRequest r) {
    LeaveRequestEntity e = leaveRepository.findById(requestId)
        .orElseThrow(() -> new EntityNotFoundException("Leave request not found: " + requestId));
    if (!"PENDING".equals(e.getStatus())) {
      throw new EntityDeletionNotAllowedException("This request is already " + e.getStatus().toLowerCase());
    }

    boolean approve = "APPROVE".equalsIgnoreCase(r.action());
    e.setStatus(approve ? "APPROVED" : "REJECTED");
    e.setApproverId(approverId);
    e.setApprovedAt(LocalDateTime.now());
    e.setDecisionNote(r.note() == null || r.note().isBlank() ? null : r.note().trim());

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

    return toResponse(leaveRepository.save(e));
  }

  /** Member cancels their own pending request. */
  @Transactional
  public LeaveRequestResponse cancel(Long requestId, Long userId) {
    LeaveRequestEntity e = leaveRepository.findById(requestId)
        .orElseThrow(() -> new EntityNotFoundException("Leave request not found: " + requestId));
    if (!e.getUserId().equals(userId)) {
      throw new EntityDeletionNotAllowedException("You can only cancel your own leave requests");
    }
    if (!"PENDING".equals(e.getStatus())) {
      throw new EntityDeletionNotAllowedException("Only pending requests can be cancelled");
    }
    e.setStatus("CANCELLED");
    return toResponse(leaveRepository.save(e));
  }

  // ---- Helpers ----

  private LeaveRequestResponse toResponse(LeaveRequestEntity e) {
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
        e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
  }
}
