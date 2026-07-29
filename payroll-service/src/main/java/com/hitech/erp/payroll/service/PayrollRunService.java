package com.hitech.erp.payroll.service;

import com.hitech.erp.common.exception.EntityDeletionNotAllowedException;
import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.payroll.db.AttendanceEntity;
import com.hitech.erp.payroll.db.AttendanceRepository;
import com.hitech.erp.payroll.db.LoanEntity;
import com.hitech.erp.payroll.db.LoanRepository;
import com.hitech.erp.payroll.db.PayrollProfileEntity;
import com.hitech.erp.payroll.db.PayrollProfileRepository;
import com.hitech.erp.payroll.db.PayrollRunEntity;
import com.hitech.erp.payroll.db.PayrollRunRepository;
import com.hitech.erp.payroll.db.PayslipEntity;
import com.hitech.erp.payroll.db.PayslipRepository;
import com.hitech.erp.payroll.db.ReimbursementEntity;
import com.hitech.erp.payroll.db.ReimbursementRepository;
import com.hitech.erp.payroll.dto.PayrollDtos.PayrollRunResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.PayrollRunSummary;
import com.hitech.erp.payroll.dto.PayrollDtos.PayslipResponse;
import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a month's attendance + salary profile + open loans + approved reimbursements into a
 * batch of payslips. Formula mirrors the client-side computePayslip() that's been used in the
 * preview for months:
 *   gross = (monthly CTC × payable days ÷ total days) for salaried,
 *           work_rate × (present + HD/2 + OT/8)         for work-basis
 *   pf    = min(basic, 15000) × 12% × (payable/total)   if PF is on
 *   esic  = gross × 0.75%                                if ESIC is on
 *   pt    = 200                                          if PT is on and gross > 15000
 *   net   = gross − pf − esic − pt − loanEmi + reimbursements
 *
 * Runs live in three states: DRAFT (regeneratable, editable), LOCKED (frozen, no re-generate),
 * PAID (payments recorded — Phase 6). Generating a month that already has a LOCKED run errors.
 */
@Service
@RequiredArgsConstructor
public class PayrollRunService {

  private final PayrollRunRepository runRepository;
  private final PayslipRepository payslipRepository;
  private final PayrollProfileRepository profileRepository;
  private final AttendanceRepository attendanceRepository;
  private final LoanRepository loanRepository;
  private final ReimbursementRepository reimbRepository;
  private final AppUserRepository userRepository;

  // ---- Reads ----

  @Transactional(readOnly = true)
  public List<PayrollRunSummary> listRuns() {
    return runRepository.findAllByOrderByMonthDesc().stream().map(this::toSummary).toList();
  }

  @Transactional(readOnly = true)
  public PayrollRunResponse getRun(String month) {
    PayrollRunEntity run = runRepository.findByMonth(month)
        .orElseThrow(() -> new EntityNotFoundException("No payroll run for " + month));
    return toResponse(run);
  }

  @Transactional(readOnly = true)
  public List<PayslipResponse> mySlips(Long userId) {
    List<PayslipEntity> rows = payslipRepository.findByUserIdOrderByIdDesc(userId);
    Map<Long, String> names = resolveNames(List.of(userId));
    return rows.stream().map(p -> toSlipResponse(p, names)).toList();
  }

  // ---- Writes ----

  /**
   * Generate (or regenerate, if DRAFT) all payslips for a month. Reads every on-payroll member's
   * attendance for the month + their profile + open loans + approved reimbursements, and computes
   * gross → deductions → net. Idempotent while DRAFT: re-running overwrites the previous slips.
   */
  @Transactional
  public PayrollRunResponse generate(String month) {
    YearMonth ym = YearMonth.parse(month);
    LocalDate from = ym.atDay(1);
    LocalDate to = ym.atEndOfMonth();
    int totalDays = ym.lengthOfMonth();

    // For salaried members we assume "present unless marked", but only for days that have actually
    // elapsed — so a mid-month draft doesn't pay for future days. A past month counts in full.
    LocalDate today = ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate();
    LocalDate lastCounted = to.isAfter(today) ? today : to;
    int elapsedDays = lastCounted.isBefore(from)
        ? 0
        : (int) (java.time.temporal.ChronoUnit.DAYS.between(from, lastCounted) + 1);

    PayrollRunEntity run = runRepository.findByMonth(month).orElseGet(() -> {
      PayrollRunEntity fresh = new PayrollRunEntity();
      fresh.setMonth(month);
      fresh.setStatus("DRAFT");
      return fresh;
    });
    if ("LOCKED".equals(run.getStatus()) || "PAID".equals(run.getStatus())) {
      throw new EntityDeletionNotAllowedException("Run for " + month + " is " + run.getStatus() + " — unlock it before regenerating");
    }
    // Fresh regenerate — wipe existing slips.
    run.getPayslips().clear();
    runRepository.saveAndFlush(run);

    // Fetch all on-payroll members + their profiles + their attendance for the month.
    List<PayrollProfileEntity> profiles = profileRepository.findAll();
    Map<Long, PayrollProfileEntity> byUser = new HashMap<>();
    for (PayrollProfileEntity p : profiles) byUser.put(p.getUserId(), p);

    List<Long> userIds = profiles.stream().map(PayrollProfileEntity::getUserId).toList();
    if (userIds.isEmpty()) {
      run.setPersonCount(0);
      run.setTotalGross(BigDecimal.ZERO);
      run.setTotalNet(BigDecimal.ZERO);
      return toResponse(runRepository.save(run));
    }
    List<AttendanceEntity> att = attendanceRepository.findByUserIdInAndDateBetween(userIds, from, to);
    Map<Long, List<AttendanceEntity>> attByUser = new HashMap<>();
    for (AttendanceEntity a : att) attByUser.computeIfAbsent(a.getUserId(), k -> new java.util.ArrayList<>()).add(a);

    BigDecimal totalGross = BigDecimal.ZERO;
    BigDecimal totalNet = BigDecimal.ZERO;

    for (PayrollProfileEntity p : profiles) {
      // Compute attendance summary for this member
      List<AttendanceEntity> theirs = attByUser.getOrDefault(p.getUserId(), List.of());
      int present = 0, halfDay = 0, paidLeave = 0, weekOff = 0, absent = 0;
      BigDecimal ot = BigDecimal.ZERO;
      for (AttendanceEntity a : theirs) {
        switch (a.getCode()) {
          case "P" -> present++;
          case "HD" -> halfDay++;
          case "PL" -> paidLeave++;
          case "WO" -> weekOff++;
          case "A" -> absent++;
        }
        ot = ot.add(a.getOvertimeHours() == null ? BigDecimal.ZERO : a.getOvertimeHours());
      }

      BigDecimal gross;
      BigDecimal payableDays;
      boolean isWork = "WORK_BASIS".equals(p.getCategory());
      if (isWork) {
        // Work-basis is paid only for days actually worked (marked present / half-day) + OT.
        BigDecimal workDays = new BigDecimal(present).add(new BigDecimal(halfDay).multiply(BigDecimal.valueOf(0.5)));
        BigDecimal otPortion = ot.divide(BigDecimal.valueOf(8), 4, RoundingMode.HALF_UP);
        payableDays = workDays;
        gross = p.getWorkRate().multiply(workDays.add(otPortion)).setScale(0, RoundingMode.HALF_UP);
      } else {
        // Salaried (REGULAR/CONTRACTOR): assume present for every elapsed day unless explicitly
        // marked Absent (full LOP) or Half-Day (½ LOP). Paid leave, week-offs and unmarked days
        // all stay payable — so a fully-unmarked past month pays the full monthly salary.
        payableDays = new BigDecimal(elapsedDays)
            .subtract(new BigDecimal(absent))
            .subtract(new BigDecimal(halfDay).multiply(BigDecimal.valueOf(0.5)))
            .max(BigDecimal.ZERO);
        gross = p.getMonthlyCtc().multiply(payableDays)
            .divide(new BigDecimal(totalDays), 0, RoundingMode.HALF_UP);
      }

      BigDecimal pf = BigDecimal.ZERO;
      if (p.isPf()) {
        BigDecimal cap = p.getBasic().min(BigDecimal.valueOf(15000));
        pf = cap.multiply(BigDecimal.valueOf(0.12))
            .multiply(payableDays)
            .divide(new BigDecimal(totalDays), 0, RoundingMode.HALF_UP);
      }
      BigDecimal esic = p.isEsic() ? gross.multiply(BigDecimal.valueOf(0.0075)).setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
      BigDecimal pt = p.isPt() && gross.compareTo(BigDecimal.valueOf(15000)) > 0 ? BigDecimal.valueOf(200) : BigDecimal.ZERO;

      // Loan EMIs (only outstanding > 0)
      List<LoanEntity> loans = loanRepository.findByUserIdOrderByCreatedAtDesc(p.getUserId());
      BigDecimal loanEmi = loans.stream()
          .filter(l -> l.getOutstanding().compareTo(BigDecimal.ZERO) > 0)
          .map(LoanEntity::getEmi)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      // Approved reimbursements not yet paid this cycle — added to net.
      List<ReimbursementEntity> reimbs = reimbRepository.findByUserIdOrderByAppliedAtDesc(p.getUserId());
      BigDecimal reimbursements = reimbs.stream()
          .filter(x -> "APPROVED".equals(x.getStatus()))
          .map(x -> x.getApprovedAmount() == null ? x.getRequestedAmount() : x.getApprovedAmount())
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal net = gross.subtract(pf).subtract(esic).subtract(pt).subtract(loanEmi).add(reimbursements)
          .max(BigDecimal.ZERO);

      PayslipEntity slip = new PayslipEntity();
      slip.setRun(run);
      slip.setUserId(p.getUserId());
      slip.setGross(gross);
      slip.setPf(pf);
      slip.setEsic(esic);
      slip.setPt(pt);
      slip.setLoanEmi(loanEmi);
      slip.setReimbursements(reimbursements);
      slip.setNet(net);
      slip.setPayableDays(payableDays);
      slip.setTotalDays(totalDays);
      run.getPayslips().add(slip);

      totalGross = totalGross.add(gross);
      totalNet = totalNet.add(net);
    }

    run.setPersonCount(profiles.size());
    run.setTotalGross(totalGross);
    run.setTotalNet(totalNet);
    return toResponse(runRepository.save(run));
  }

  @Transactional
  public PayrollRunResponse lock(String month, Long lockedBy) {
    PayrollRunEntity run = runRepository.findByMonth(month)
        .orElseThrow(() -> new EntityNotFoundException("No payroll run for " + month));
    if (!"DRAFT".equals(run.getStatus())) {
      throw new EntityDeletionNotAllowedException("Only DRAFT runs can be locked (this one is " + run.getStatus() + ")");
    }
    run.setStatus("LOCKED");
    run.setLockedBy(lockedBy);
    run.setLockedAt(LocalDateTime.now());
    return toResponse(runRepository.save(run));
  }

  /**
   * Record that a LOCKED run was paid out manually (bank transfer / cash done by Hi-Tech by hand).
   * This only marks the run PAID — it never moves any money. PAID runs are frozen.
   */
  @Transactional
  public PayrollRunResponse markPaid(String month, Long paidBy) {
    PayrollRunEntity run = runRepository.findByMonth(month)
        .orElseThrow(() -> new EntityNotFoundException("No payroll run for " + month));
    if (!"LOCKED".equals(run.getStatus())) {
      throw new EntityDeletionNotAllowedException("Only a LOCKED run can be marked paid (this one is " + run.getStatus() + ") — lock it first.");
    }
    run.setStatus("PAID");
    run.setPaidBy(paidBy);
    run.setPaidAt(LocalDateTime.now());
    return toResponse(runRepository.save(run));
  }

  /**
   * Step a run back one state: PAID → LOCKED (undo a mistaken "mark paid" — it's only a record, no
   * money moved), or LOCKED → DRAFT (reopen for regeneration). A DRAFT run has nothing to undo.
   */
  @Transactional
  public PayrollRunResponse unlock(String month) {
    PayrollRunEntity run = runRepository.findByMonth(month)
        .orElseThrow(() -> new EntityNotFoundException("No payroll run for " + month));
    if ("PAID".equals(run.getStatus())) {
      run.setStatus("LOCKED");
      run.setPaidBy(null);
      run.setPaidAt(null);
    } else if ("LOCKED".equals(run.getStatus())) {
      run.setStatus("DRAFT");
      run.setLockedBy(null);
      run.setLockedAt(null);
    } else {
      throw new EntityDeletionNotAllowedException("This run is already a draft — nothing to unlock.");
    }
    return toResponse(runRepository.save(run));
  }

  // ---- Helpers ----

  private PayrollRunResponse toResponse(PayrollRunEntity run) {
    List<Long> ids = new java.util.ArrayList<>(run.getPayslips().stream().map(PayslipEntity::getUserId).toList());
    if (run.getLockedBy() != null) ids.add(run.getLockedBy());
    if (run.getPaidBy() != null) ids.add(run.getPaidBy());
    Map<Long, String> names = resolveNames(ids);
    List<PayslipResponse> slips = run.getPayslips().stream().map(p -> toSlipResponse(p, names)).toList();
    return new PayrollRunResponse(
        run.getId(), run.getMonth(), run.getStatus(),
        run.getTotalGross(), run.getTotalNet(), run.getPersonCount(),
        run.getLockedBy(), run.getLockedBy() != null ? names.getOrDefault(run.getLockedBy(), "") : null,
        run.getLockedAt() == null ? null : run.getLockedAt().toString(),
        run.getCreatedAt() == null ? null : run.getCreatedAt().toString(),
        run.getPaidAt() == null ? null : run.getPaidAt().toString(),
        run.getPaidBy() != null ? names.getOrDefault(run.getPaidBy(), "") : null,
        slips);
  }

  private PayrollRunSummary toSummary(PayrollRunEntity r) {
    return new PayrollRunSummary(
        r.getId(), r.getMonth(), r.getStatus(),
        r.getTotalGross(), r.getTotalNet(), r.getPersonCount(),
        r.getCreatedAt() == null ? null : r.getCreatedAt().toString(),
        r.getPaidAt() == null ? null : r.getPaidAt().toString());
  }

  private PayslipResponse toSlipResponse(PayslipEntity p, Map<Long, String> names) {
    return new PayslipResponse(
        p.getId(), p.getUserId(), names.getOrDefault(p.getUserId(), ""),
        p.getGross(), p.getPf(), p.getEsic(), p.getPt(),
        p.getLoanEmi(), p.getReimbursements(), p.getNet(),
        p.getPayableDays(), p.getTotalDays(),
        p.getRun() == null ? null : p.getRun().getMonth());
  }

  private Map<Long, String> resolveNames(List<Long> ids) {
    List<Long> distinct = ids.stream().distinct().toList();
    if (distinct.isEmpty()) return Map.of();
    Map<Long, String> out = new HashMap<>();
    for (AppUserEntity u : userRepository.findAllById(distinct)) out.put(u.getId(), u.getFullName());
    return out;
  }
}
