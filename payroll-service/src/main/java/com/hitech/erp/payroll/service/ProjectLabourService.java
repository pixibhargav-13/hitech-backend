package com.hitech.erp.payroll.service;

import com.hitech.erp.payroll.db.AttendanceEntity;
import com.hitech.erp.payroll.db.AttendanceRepository;
import com.hitech.erp.payroll.db.PayrollProfileEntity;
import com.hitech.erp.payroll.db.PayrollProfileRepository;
import com.hitech.erp.payroll.dto.PayrollDtos.ProjectManpower;
import com.hitech.erp.payroll.dto.PayrollDtos.ProjectManpowerDay;
import com.hitech.erp.payroll.dto.PayrollDtos.ProjectStaffRow;
import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attributes labour to projects.
 *
 * <p>Attendance already records <em>where</em> someone punched ({@code payroll_attendance
 * .project_id}), which makes it the only honest basis for splitting a workforce across sites: a
 * member who spends half a month on one site and half on another is counted on each for the days
 * they were actually there.
 *
 * <p><b>Cost here is an allocation, not a payslip.</b> {@link PayrollRunService} remains the single
 * authority on what a member is actually paid — that calculation runs per month, applies deductions,
 * loans and reimbursements, and knows nothing about sites. This service answers the different
 * question a site manager asks: "what is this project's labour costing me?" It multiplies days
 * worked on the project by the member's daily rate, so the per-project figures will not sum exactly
 * to a month's payroll (unmarked days, deductions and reimbursements all sit outside it).
 *
 * <p>Project membership lives in the project module, which this module deliberately doesn't depend
 * on — callers pass the member ids in.
 */
@Service
@RequiredArgsConstructor
public class ProjectLabourService {

  /** Divisor turning a monthly salary into a day rate for allocation. */
  private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);

  /** A day of overtime, for folding OT hours into work-basis cost the way payroll runs do. */
  private static final BigDecimal HOURS_PER_DAY = BigDecimal.valueOf(8);

  private static final BigDecimal HALF = BigDecimal.valueOf(0.5);

  private final AttendanceRepository attendanceRepository;
  private final PayrollProfileRepository profileRepository;
  private final AppUserRepository userRepository;

  /** Headcount, man-days and allocated cost for one project over a window. */
  @Transactional(readOnly = true)
  public ProjectManpower manpower(Long projectId, List<Long> memberIds, LocalDate from, LocalDate to) {
    List<AttendanceEntity> rows =
        attendanceRepository.findByProjectIdAndDateBetweenOrderByDateAsc(projectId, from, to);

    Map<Long, PayrollProfileEntity> profiles = profilesFor(userIdsIn(rows));

    BigDecimal manDays = BigDecimal.ZERO;
    BigDecimal overtime = BigDecimal.ZERO;
    BigDecimal cost = BigDecimal.ZERO;
    boolean incomplete = false;
    Set<Long> active = new LinkedHashSet<>();
    // Sorted so the trend reads left-to-right by date regardless of insertion order.
    Map<LocalDate, DayBucket> byDay = new TreeMap<>();

    for (AttendanceEntity a : rows) {
      BigDecimal days = payableFraction(a.getCode());
      BigDecimal ot = nz(a.getOvertimeHours());
      manDays = manDays.add(days);
      overtime = overtime.add(ot);
      if (days.signum() > 0) active.add(a.getUserId());

      PayrollProfileEntity p = profiles.get(a.getUserId());
      if (p == null) {
        if (days.signum() > 0) incomplete = true;
      } else {
        cost = cost.add(costOf(p, days, ot));
      }

      DayBucket bucket = byDay.computeIfAbsent(a.getDate(), d -> new DayBucket());
      if (days.signum() > 0) {
        bucket.workers++;
        bucket.manDays = bucket.manDays.add(days);
      }
    }

    List<ProjectManpowerDay> trend = new ArrayList<>();
    byDay.forEach((d, b) -> trend.add(new ProjectManpowerDay(d.toString(), b.workers, scale(b.manDays))));

    // "Today" is the last day in the requested window, not the wall clock — a manager looking at
    // last week should see last Friday's headcount, not an empty row.
    int presentToday = byDay.isEmpty() ? 0 : byDay.get(byDay.keySet().stream().reduce((a, b) -> b).orElseThrow()).workers;

    return new ProjectManpower(
        memberIds == null ? 0 : (int) memberIds.stream().distinct().count(),
        active.size(),
        presentToday,
        scale(manDays),
        scale(overtime),
        money(cost),
        incomplete,
        trend);
  }

  /**
   * Per-member breakdown for the project's Staff tab: everyone assigned to the project, plus anyone
   * who actually punched on it (a contractor lent to the site for a week shows up without being a
   * permanent member).
   *
   * @param includeMoney false strips rate and cost — a site supervisor sees who worked and for how
   *     long, but not what anyone earns.
   */
  @Transactional(readOnly = true)
  public List<ProjectStaffRow> staff(
      Long projectId, List<Long> memberIds, LocalDate from, LocalDate to, boolean includeMoney) {

    List<AttendanceEntity> rows =
        attendanceRepository.findByProjectIdAndDateBetweenOrderByDateAsc(projectId, from, to);

    Set<Long> userIds = new LinkedHashSet<>(memberIds == null ? List.of() : memberIds);
    userIds.addAll(userIdsIn(rows));
    if (userIds.isEmpty()) return List.of();

    Map<Long, AppUserEntity> users = new HashMap<>();
    for (AppUserEntity u : userRepository.findAllById(userIds)) users.put(u.getId(), u);
    Map<Long, PayrollProfileEntity> profiles = profilesFor(List.copyOf(userIds));

    Map<Long, Tally> tallies = new HashMap<>();
    for (AttendanceEntity a : rows) {
      Tally t = tallies.computeIfAbsent(a.getUserId(), k -> new Tally());
      BigDecimal days = payableFraction(a.getCode());
      switch (a.getCode()) {
        case "P" -> t.present++;
        case "HD" -> t.present++;
        case "A" -> t.absent++;
        default -> { /* PL / WO / NM are neither worked nor absent on this site */ }
      }
      t.manDays = t.manDays.add(days);
      t.overtime = t.overtime.add(nz(a.getOvertimeHours()));
      if (t.lastSeen == null || a.getDate().isAfter(t.lastSeen)) t.lastSeen = a.getDate();
    }

    List<ProjectStaffRow> out = new ArrayList<>();
    for (Long userId : userIds) {
      AppUserEntity u = users.get(userId);
      if (u == null) continue; // deleted member with orphaned attendance
      PayrollProfileEntity p = profiles.get(userId);
      Tally t = tallies.getOrDefault(userId, new Tally());

      BigDecimal dayRate = p == null ? null : dayRateOf(p);
      BigDecimal cost = p == null ? BigDecimal.ZERO : costOf(p, t.manDays, t.overtime);

      out.add(new ProjectStaffRow(
          userId,
          u.getFullName(),
          u.getEmail(),
          u.getPhoneNumber(),
          u.getStaffType(),
          u.getDepartment() == null ? null : u.getDepartment().getName(),
          u.getRole() == null ? null : u.getRole().getName(),
          u.getPhotoUrl(),
          p == null ? null : p.getDesignation(),
          p == null ? null : p.getCategory(),
          p == null ? null : p.getWorkType(),
          includeMoney ? dayRate : null,
          t.present,
          t.absent,
          scale(t.manDays),
          scale(t.overtime),
          includeMoney ? money(cost) : null,
          t.lastSeen == null ? null : t.lastSeen.toString()));
    }

    // Busiest on site first — that's the order a site manager scans in.
    out.sort(Comparator.comparing(ProjectStaffRow::manDays).reversed()
        .thenComparing(r -> r.name() == null ? "" : r.name()));
    return out;
  }

  // ---- helpers ----

  /**
   * What one attendance row contributes in payable days on this site. Only days actually worked
   * count: paid leave and week-offs are payroll's problem, not the site's, and attributing them to
   * whichever project the member last punched at would overstate that project's cost.
   */
  private static BigDecimal payableFraction(String code) {
    if (code == null) return BigDecimal.ZERO;
    return switch (code) {
      case "P" -> BigDecimal.ONE;
      case "HD" -> HALF;
      default -> BigDecimal.ZERO;
    };
  }

  /** A member's cost for a number of days on site, plus overtime, at their own rate. */
  private BigDecimal costOf(PayrollProfileEntity p, BigDecimal days, BigDecimal overtimeHours) {
    BigDecimal rate = dayRateOf(p);
    BigDecimal otDays = overtimeHours.divide(HOURS_PER_DAY, 4, RoundingMode.HALF_UP);
    return rate.multiply(days.add(otDays));
  }

  /**
   * Day rate for allocation. Work-basis members already carry one; salaried members get their
   * monthly CTC spread over a nominal 30-day month, which keeps the rate stable across months of
   * different lengths.
   */
  private BigDecimal dayRateOf(PayrollProfileEntity p) {
    if ("WORK_BASIS".equals(p.getCategory())) return nz(p.getWorkRate());
    return nz(p.getMonthlyCtc()).divide(DAYS_PER_MONTH, 2, RoundingMode.HALF_UP);
  }

  private Map<Long, PayrollProfileEntity> profilesFor(List<Long> userIds) {
    Map<Long, PayrollProfileEntity> out = new HashMap<>();
    if (userIds.isEmpty()) return out;
    for (PayrollProfileEntity p : profileRepository.findAllByUserIdIn(userIds)) out.put(p.getUserId(), p);
    return out;
  }

  private static List<Long> userIdsIn(List<AttendanceEntity> rows) {
    return rows.stream().map(AttendanceEntity::getUserId).distinct().toList();
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static BigDecimal scale(BigDecimal v) {
    return nz(v).setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal money(BigDecimal v) {
    return nz(v).setScale(2, RoundingMode.HALF_UP);
  }

  private static final class DayBucket {
    int workers;
    BigDecimal manDays = BigDecimal.ZERO;
  }

  private static final class Tally {
    int present;
    int absent;
    BigDecimal manDays = BigDecimal.ZERO;
    BigDecimal overtime = BigDecimal.ZERO;
    LocalDate lastSeen;
  }
}
