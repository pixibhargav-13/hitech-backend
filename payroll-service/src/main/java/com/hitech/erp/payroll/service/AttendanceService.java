package com.hitech.erp.payroll.service;

import com.hitech.erp.payroll.db.AttendanceEntity;
import com.hitech.erp.payroll.db.AttendanceRepository;
import com.hitech.erp.payroll.db.FaceEnrollmentEntity;
import com.hitech.erp.payroll.db.FaceEnrollmentRepository;
import com.hitech.erp.payroll.db.PayrollProfileEntity;
import com.hitech.erp.payroll.db.PayrollProfileRepository;
import com.hitech.erp.payroll.db.ShiftEntity;
import com.hitech.erp.payroll.db.ShiftRepository;
import com.hitech.erp.payroll.dto.PayrollDtos.AttendanceEditRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.AttendanceResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.FaceEnrollmentRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.FaceEnrollmentResponse;
import com.hitech.erp.common.context.RequestProjectContext;
import com.hitech.erp.payroll.dto.PayrollDtos.PunchRequest;
import com.hitech.erp.usermanagement.access.ProjectScopeResolver;
import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists attendance rows — one per (member, date). Backs both the self-service punch flow
 * (POST /punch — user id from JWT) and the admin muster/edit surface. Codes: P/A/HD/PL/WO/NM.
 * All times use the local calendar (Asia/Kolkata) so a punch just before midnight IST files
 * under the correct human day, not the previous UTC day.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

  private static final ZoneId LOCAL = ZoneId.of("Asia/Kolkata");

  private final AttendanceRepository attendanceRepository;
  private final AppUserRepository userRepository;
  private final FaceEnrollmentRepository faceRepository;
  private final LocationService locationService;
  private final ProjectScopeResolver projectScope;
  private final PayrollProfileRepository profileRepository;
  private final ShiftRepository shiftRepository;

  // ---- Reads ----

  @Transactional(readOnly = true)
  public List<AttendanceResponse> getForMember(Long userId, LocalDate from, LocalDate to) {
    List<AttendanceEntity> rows = attendanceRepository.findByUserIdAndDateBetweenOrderByDateAsc(userId, from, to);
    Map<Long, String> names = resolveNames(rows);
    return rows.stream().map(r -> toResponse(r, names)).toList();
  }

  @Transactional(readOnly = true)
  public List<AttendanceResponse> getMuster(LocalDate from, LocalDate to) {
    List<AttendanceEntity> rows = attendanceRepository.findByDateBetweenOrderByDateAsc(from, to);
    Map<Long, String> names = resolveNames(rows);
    return rows.stream()
        .sorted(Comparator.comparing((AttendanceEntity r) -> names.getOrDefault(r.getUserId(), ""))
            .thenComparing(AttendanceEntity::getDate))
        .map(r -> toResponse(r, names)).toList();
  }

  @Transactional(readOnly = true)
  public List<AttendanceResponse> getForProject(Long projectId, LocalDate from, LocalDate to) {
    // Holding PAYROLL:MANAGE isn't the same as being on this site — check membership too.
    projectScope.resolve(projectId);
    List<AttendanceEntity> rows = attendanceRepository.findByProjectIdAndDateBetweenOrderByDateAsc(projectId, from, to);
    Map<Long, String> names = resolveNames(rows);
    return rows.stream().map(r -> toResponse(r, names)).toList();
  }

  // ---- Writes ----

  /** Punch in/out for the signed-in member — caller passes the direction; user id + date come from context. */
  @Transactional
  public AttendanceResponse punch(Long userId, PunchRequest r) {
    // Geofence: a member can only punch in/out while inside one of their assigned work sites.
    locationService.assertInsideAssignedSite(
        userId,
        r.lat() == null ? null : r.lat().doubleValue(),
        r.lng() == null ? null : r.lng().doubleValue());

    LocalDate today = ZonedDateTime.now(LOCAL).toLocalDate();
    String nowHm = String.format("%02d:%02d", LocalTime.now(LOCAL).getHour(), LocalTime.now(LOCAL).getMinute());

    AttendanceEntity a = attendanceRepository.findByUserIdAndDate(userId, today).orElseGet(() -> {
      AttendanceEntity fresh = new AttendanceEntity();
      fresh.setUserId(userId);
      fresh.setDate(today);
      return fresh;
    });

    boolean isIn = "IN".equalsIgnoreCase(r.direction());
    if (isIn) {
      a.setInTime(nowHm);
      a.setPunchInLat(r.lat());
      a.setPunchInLng(r.lng());
      a.setFaceScoreIn(r.faceScore());
      if (r.photo() != null && !r.photo().isBlank()) a.setPunchInPhoto(r.photo());
    } else {
      a.setOutTime(nowHm);
      a.setPunchOutLat(r.lat());
      a.setPunchOutLng(r.lng());
      a.setFaceScoreOut(r.faceScore());
      if (r.photo() != null && !r.photo().isBlank()) a.setPunchOutPhoto(r.photo());
    }
    if (r.projectId() != null) a.setProjectId(r.projectId());
    RequestProjectContext.set(a.getProjectId());

    // Punching in marks presence provisionally; only a completed pair can say how long the day was,
    // so the code and overtime are derived on punch-out against the member's shift. Previously any
    // punch set "P" outright, which paid a full day for a ten-minute appearance.
    a.setCode("P");
    applyShiftDerivation(a);

    AttendanceEntity saved = attendanceRepository.save(a);
    return toResponse(saved, resolveNames(List.of(saved)));
  }

  /** Admin edit — set/adjust an attendance row (mark absent, set OT, tweak times). */
  @Transactional
  public AttendanceResponse edit(AttendanceEditRequest r) {
    LocalDate date = LocalDate.parse(r.date());
    AttendanceEntity a = attendanceRepository.findByUserIdAndDate(r.userId(), date).orElseGet(() -> {
      AttendanceEntity fresh = new AttendanceEntity();
      fresh.setUserId(r.userId());
      fresh.setDate(date);
      return fresh;
    });
    boolean explicitCode = r.code() != null && !r.code().isBlank();
    if (explicitCode) a.setCode(r.code());
    if (r.inTime() != null) a.setInTime(r.inTime().isBlank() ? null : r.inTime());
    if (r.outTime() != null) a.setOutTime(r.outTime().isBlank() ? null : r.outTime());

    // Times drive the derivation; an explicit code from an admin overrides it (they may know the
    // member was on site without a clean punch). Overtime is derived unless typed in directly.
    boolean derived = applyShiftDerivation(a);
    if (explicitCode) a.setCode(r.code());
    if (r.overtimeHours() != null) a.setOvertimeHours(r.overtimeHours());
    else if (!derived && (r.inTime() != null || r.outTime() != null)) a.setOvertimeHours(BigDecimal.ZERO);
    if (r.fineHours() != null) a.setFineHours(r.fineHours());
    // A day's labour can only be booked to a site the editor can reach — otherwise cost lands on
    // a project they can't open.
    projectScope.assertCanWrite(r.projectId());
    if (r.projectId() != null) a.setProjectId(r.projectId());
    RequestProjectContext.set(a.getProjectId());
    AttendanceEntity saved = attendanceRepository.save(a);
    return toResponse(saved, resolveNames(List.of(saved)));
  }

  /** Admin housekeeping — clear every attendance row in a date range (e.g. to reset before a test). */
  @Transactional
  public int clearRange(LocalDate from, LocalDate to) {
    return attendanceRepository.deleteByDateBetween(from, to);
  }

  /** Convenience for the frontend — get the current user's record for today, or a stub. */
  @Transactional(readOnly = true)
  public AttendanceResponse getToday(Long userId) {
    LocalDate today = ZonedDateTime.now(LOCAL).toLocalDate();
    return attendanceRepository.findByUserIdAndDate(userId, today)
        .map(a -> toResponse(a, resolveNames(List.of(a))))
        .orElse(new AttendanceResponse(
            null, userId, resolveNames(List.of()).getOrDefault(userId, ""), today.toString(), "NM",
            null, null, BigDecimal.ZERO, BigDecimal.ZERO, null, null,
            null, null, null, null, null, null, null, null));
  }

  // ---- Helpers ----

  /**
   * Recompute code / worked hours / overtime for a row from its punch pair and the member's shift.
   *
   * @return true when a complete pair was found and the derivation applied
   */
  private boolean applyShiftDerivation(AttendanceEntity a) {
    ShiftRules.Evaluation eval = ShiftRules.evaluate(shiftFor(a.getUserId()), a.getInTime(), a.getOutTime());
    if (eval == null) {
      // Incomplete pair — record that explicitly rather than leaving a stale figure behind.
      a.setWorkedHours(null);
      return false;
    }
    a.setCode(eval.code());
    a.setWorkedHours(eval.workedHours());
    a.setOvertimeHours(eval.overtimeHours());
    return true;
  }

  /** The shift assigned to a member, or null when they have no payroll profile yet. */
  private ShiftEntity shiftFor(Long userId) {
    return profileRepository
        .findByUserId(userId)
        .map(PayrollProfileEntity::getShiftId)
        .flatMap(id -> id == null ? java.util.Optional.empty() : shiftRepository.findById(id))
        .orElse(null);
  }

  private Map<Long, String> resolveNames(List<AttendanceEntity> rows) {
    List<Long> ids = rows.stream().map(AttendanceEntity::getUserId).distinct().toList();
    if (ids.isEmpty()) return Map.of();
    Map<Long, String> out = new HashMap<>();
    for (AppUserEntity u : userRepository.findAllById(ids)) out.put(u.getId(), u.getFullName());
    return out;
  }

  private AttendanceResponse toResponse(AttendanceEntity a, Map<Long, String> names) {
    return new AttendanceResponse(
        a.getId(), a.getUserId(), names.getOrDefault(a.getUserId(), ""),
        a.getDate().toString(), a.getCode(),
        a.getInTime(), a.getOutTime(),
        a.getOvertimeHours(), a.getFineHours(),
        a.getWorkedHours(),
        a.getProjectId(),
        a.getPunchInLat(), a.getPunchInLng(),
        a.getPunchOutLat(), a.getPunchOutLng(),
        a.getFaceScoreIn(), a.getFaceScoreOut(),
        a.getPunchInPhoto(), a.getPunchOutPhoto());
  }

  // ---- Face enrolment (self-service) ----

  /** The signed-in member's enrolled reference face, or enrolled=false when none is set yet. */
  @Transactional(readOnly = true)
  public FaceEnrollmentResponse getFace(Long userId) {
    return faceRepository.findByUserId(userId)
        .map(e -> new FaceEnrollmentResponse(parseDescriptor(e.getDescriptor()), e.getPhoto(), true))
        .orElse(new FaceEnrollmentResponse(null, null, false));
  }

  /** Register (or re-register) the member's reference faceprint + selfie. */
  @Transactional
  public FaceEnrollmentResponse saveFace(Long userId, FaceEnrollmentRequest r) {
    FaceEnrollmentEntity e = faceRepository.findByUserId(userId).orElseGet(() -> {
      FaceEnrollmentEntity fresh = new FaceEnrollmentEntity();
      fresh.setUserId(userId);
      return fresh;
    });
    e.setDescriptor(r.descriptor().stream().map(String::valueOf).collect(Collectors.joining(",")));
    e.setPhoto(r.photo() == null || r.photo().isBlank() ? null : r.photo());
    FaceEnrollmentEntity saved = faceRepository.save(e);
    return new FaceEnrollmentResponse(parseDescriptor(saved.getDescriptor()), saved.getPhoto(), true);
  }

  private static List<Double> parseDescriptor(String csv) {
    if (csv == null || csv.isBlank()) return List.of();
    return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Double::parseDouble).toList();
  }
}
