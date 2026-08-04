package com.hitech.erp.payroll.service;

import com.hitech.erp.common.exception.EntityDeletionNotAllowedException;
import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.payroll.db.HolidayEntity;
import com.hitech.erp.payroll.db.HolidayPolicyEntity;
import com.hitech.erp.payroll.db.HolidayPolicyRepository;
import com.hitech.erp.payroll.db.LeavePolicyEntity;
import com.hitech.erp.payroll.db.LeavePolicyRepository;
import com.hitech.erp.payroll.db.LeaveTypeEntity;
import com.hitech.erp.payroll.db.PayrollProfileEntity;
import com.hitech.erp.payroll.db.PayrollProfileRepository;
import com.hitech.erp.payroll.db.SalaryTemplateEntity;
import com.hitech.erp.payroll.db.SalaryTemplateRepository;
import com.hitech.erp.payroll.db.ShiftEntity;
import com.hitech.erp.payroll.db.ShiftRepository;
import com.hitech.erp.payroll.dto.PayrollDtos.HolidayPolicyRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.HolidayPolicyResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.HolidayRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.HolidayResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.LeavePolicyRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.LeavePolicyResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.LeaveTypeRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.LeaveTypeResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.PayrollProfileRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.PayrollProfileResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.SalaryStructure;
import com.hitech.erp.payroll.dto.PayrollDtos.SalaryTemplateRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.SalaryTemplateResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.ShiftRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.ShiftResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payroll setup (shifts, holiday policies, leave policies) and per-member payroll profiles.
 * Everything here is "pre-setup then assign" — policies are configured once and referenced by
 * id from each member's profile, so editing a policy updates every member assigned to it.
 */
@Service
@RequiredArgsConstructor
public class PayrollService {

  private final ShiftRepository shiftRepository;
  private final HolidayPolicyRepository holidayPolicyRepository;
  private final LeavePolicyRepository leavePolicyRepository;
  private final PayrollProfileRepository profileRepository;
  private final SalaryTemplateRepository salaryTemplateRepository;

  // ================= Salary component template =================

  /** The org-wide default salary components (delimited text). Empty when never set up. */
  @Transactional(readOnly = true)
  public SalaryTemplateResponse getSalaryTemplate() {
    return salaryTemplateRepository.findAll().stream()
        .findFirst()
        .map(t -> new SalaryTemplateResponse(t.getComponents()))
        .orElse(new SalaryTemplateResponse(null));
  }

  @Transactional
  public SalaryTemplateResponse saveSalaryTemplate(SalaryTemplateRequest r) {
    SalaryTemplateEntity t = salaryTemplateRepository.findAll().stream().findFirst().orElseGet(SalaryTemplateEntity::new);
    t.setComponents(blankToNull(r.components()));
    return new SalaryTemplateResponse(salaryTemplateRepository.save(t).getComponents());
  }

  // ================= Shifts =================

  @Transactional(readOnly = true)
  public List<ShiftResponse> getShifts() {
    return shiftRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
  }

  @Transactional
  public ShiftResponse createShift(ShiftRequest r) {
    ShiftEntity e = new ShiftEntity();
    applyShift(e, r);
    return toResponse(shiftRepository.save(e));
  }

  @Transactional
  public ShiftResponse updateShift(Long id, ShiftRequest r) {
    ShiftEntity e = requireShift(id);
    applyShift(e, r);
    return toResponse(shiftRepository.save(e));
  }

  @Transactional
  public void deleteShift(Long id) {
    requireShift(id);
    if (profileRepository.existsByShiftId(id)) {
      throw new EntityDeletionNotAllowedException("This shift is assigned to one or more members — reassign them first");
    }
    shiftRepository.deleteById(id);
  }

  private void applyShift(ShiftEntity e, ShiftRequest r) {
    e.setName(r.name().trim());
    e.setStartTime(r.startTime());
    e.setEndTime(r.endTime());
    e.setWeeklyOffs(r.weeklyOffs() == null ? "" : r.weeklyOffs().stream().map(String::valueOf).collect(Collectors.joining(",")));
    e.setGraceMinutes(r.graceMinutes());
    e.setHalfDayHours(r.halfDayHours());
    e.setFullDayHours(r.fullDayHours());
    e.setOvertimeEnabled(r.overtimeEnabled());
  }

  private ShiftResponse toResponse(ShiftEntity e) {
    List<Integer> offs = e.getWeeklyOffs() == null || e.getWeeklyOffs().isBlank()
        ? List.of()
        : Arrays.stream(e.getWeeklyOffs().split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();
    return new ShiftResponse(e.getId(), e.getName(), e.getStartTime(), e.getEndTime(), offs, e.getGraceMinutes(), e.getHalfDayHours(), e.getFullDayHours(), e.isOvertimeEnabled());
  }

  private ShiftEntity requireShift(Long id) {
    return shiftRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Shift not found: " + id));
  }

  // ================= Holiday Policies =================

  @Transactional(readOnly = true)
  public List<HolidayPolicyResponse> getHolidayPolicies() {
    return holidayPolicyRepository.findAllByOrderByYearDescNameAsc().stream().map(this::toResponse).toList();
  }

  @Transactional
  public HolidayPolicyResponse createHolidayPolicy(HolidayPolicyRequest r) {
    HolidayPolicyEntity e = new HolidayPolicyEntity();
    applyHolidayPolicy(e, r);
    return toResponse(holidayPolicyRepository.save(e));
  }

  @Transactional
  public HolidayPolicyResponse updateHolidayPolicy(Long id, HolidayPolicyRequest r) {
    HolidayPolicyEntity e = requireHolidayPolicy(id);
    applyHolidayPolicy(e, r);
    return toResponse(holidayPolicyRepository.save(e));
  }

  @Transactional
  public void deleteHolidayPolicy(Long id) {
    requireHolidayPolicy(id);
    if (profileRepository.existsByHolidayPolicyId(id)) {
      throw new EntityDeletionNotAllowedException("This holiday policy is assigned to one or more members — reassign them first");
    }
    holidayPolicyRepository.deleteById(id);
  }

  private void applyHolidayPolicy(HolidayPolicyEntity e, HolidayPolicyRequest r) {
    e.setName(r.name().trim());
    e.setYear(r.year());
    e.getHolidays().clear();
    if (r.holidays() != null) {
      for (HolidayRequest h : r.holidays()) {
        HolidayEntity he = new HolidayEntity();
        he.setPolicy(e);
        he.setDate(LocalDate.parse(h.date()));
        he.setName(h.name().trim());
        he.setType(h.type() == null || h.type().isBlank() ? "PUBLIC" : h.type());
        e.getHolidays().add(he);
      }
    }
  }

  private HolidayPolicyResponse toResponse(HolidayPolicyEntity e) {
    List<HolidayResponse> holidays = e.getHolidays().stream()
        .map(h -> new HolidayResponse(h.getDate().toString(), h.getName(), h.getType()))
        .toList();
    return new HolidayPolicyResponse(e.getId(), e.getName(), e.getYear(), holidays);
  }

  private HolidayPolicyEntity requireHolidayPolicy(Long id) {
    return holidayPolicyRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Holiday policy not found: " + id));
  }

  // ================= Leave Policies =================

  @Transactional(readOnly = true)
  public List<LeavePolicyResponse> getLeavePolicies() {
    return leavePolicyRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
  }

  @Transactional
  public LeavePolicyResponse createLeavePolicy(LeavePolicyRequest r) {
    LeavePolicyEntity e = new LeavePolicyEntity();
    applyLeavePolicy(e, r);
    return toResponse(leavePolicyRepository.save(e));
  }

  @Transactional
  public LeavePolicyResponse updateLeavePolicy(Long id, LeavePolicyRequest r) {
    LeavePolicyEntity e = requireLeavePolicy(id);
    applyLeavePolicy(e, r);
    return toResponse(leavePolicyRepository.save(e));
  }

  @Transactional
  public void deleteLeavePolicy(Long id) {
    requireLeavePolicy(id);
    if (profileRepository.existsByLeavePolicyId(id)) {
      throw new EntityDeletionNotAllowedException("This leave policy is assigned to one or more members — reassign them first");
    }
    leavePolicyRepository.deleteById(id);
  }

  private void applyLeavePolicy(LeavePolicyEntity e, LeavePolicyRequest r) {
    e.setName(r.name().trim());
    e.setCycle(r.cycle() == null || r.cycle().isBlank() ? "YEARLY" : r.cycle());
    e.getTypes().clear();
    if (r.types() != null) {
      for (LeaveTypeRequest t : r.types()) {
        LeaveTypeEntity te = new LeaveTypeEntity();
        te.setPolicy(e);
        te.setName(t.name().trim());
        te.setAnnualCount(t.annualCount());
        te.setAccrual(t.accrual() == null || t.accrual().isBlank() ? "ALL_AT_ONCE" : t.accrual());
        te.setPaid(t.paid());
        e.getTypes().add(te);
      }
    }
  }

  private LeavePolicyResponse toResponse(LeavePolicyEntity e) {
    List<LeaveTypeResponse> types = e.getTypes().stream()
        .map(t -> new LeaveTypeResponse(t.getName(), t.getAnnualCount(), t.getAccrual(), t.isPaid()))
        .toList();
    return new LeavePolicyResponse(e.getId(), e.getName(), e.getCycle(), types);
  }

  private LeavePolicyEntity requireLeavePolicy(Long id) {
    return leavePolicyRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Leave policy not found: " + id));
  }

  // ================= Payroll Profiles =================

  @Transactional(readOnly = true)
  public List<PayrollProfileResponse> getProfiles(List<Long> userIds) {
    List<PayrollProfileEntity> entities = userIds == null || userIds.isEmpty()
        ? profileRepository.findAll()
        : profileRepository.findAllByUserIdIn(userIds);
    return entities.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public PayrollProfileResponse getProfile(Long userId) {
    return profileRepository.findByUserId(userId).map(this::toResponse)
        .orElseThrow(() -> new EntityNotFoundException("No payroll profile for member: " + userId));
  }

  /** Create-or-update — a member has at most one profile, keyed by userId. */
  @Transactional
  public PayrollProfileResponse saveProfile(PayrollProfileRequest r) {
    PayrollProfileEntity e = profileRepository.findByUserId(r.userId()).orElseGet(() -> {
      PayrollProfileEntity fresh = new PayrollProfileEntity();
      fresh.setUserId(r.userId());
      return fresh;
    });

    e.setCategory(r.category());
    e.setDesignation(r.designation());
    e.setJoiningDate(r.joiningDate() == null || r.joiningDate().isBlank() ? null : LocalDate.parse(r.joiningDate()));

    SalaryStructure s = r.salary();
    if (s != null) {
      e.setMonthlyCtc(nz(s.monthlyCtc()));
      e.setBasic(nz(s.basic()));
      e.setHra(nz(s.hra()));
      e.setOtherAllowances(nz(s.otherAllowances()));
      e.setWorkType(s.workType());
      e.setWorkRate(nz(s.workRate()));
      e.setPf(s.pf());
      e.setEsic(s.esic());
      e.setPt(s.pt());
    }

    e.setBankAccount(blankToNull(r.bankAccount()));
    e.setIfsc(blankToNull(r.ifsc()));
    e.setBankName(blankToNull(r.bankName()));
    e.setPan(blankToNull(r.pan()));
    e.setDocuments(blankToNull(r.documents()));
    e.setComponents(blankToNull(r.components()));
    e.setShiftId(r.shiftId());
    e.setHolidayPolicyId(r.holidayPolicyId());
    e.setLeavePolicyId(r.leavePolicyId());

    return toResponse(profileRepository.save(e));
  }

  @Transactional
  public void deleteProfile(Long userId) {
    PayrollProfileEntity e = profileRepository.findByUserId(userId)
        .orElseThrow(() -> new EntityNotFoundException("No payroll profile for member: " + userId));
    profileRepository.delete(e);
  }

  private PayrollProfileResponse toResponse(PayrollProfileEntity e) {
    SalaryStructure salary = new SalaryStructure(
        e.getMonthlyCtc(), e.getBasic(), e.getHra(), e.getOtherAllowances(),
        e.getWorkType(), e.getWorkRate(), e.isPf(), e.isEsic(), e.isPt());
    return new PayrollProfileResponse(
        e.getUserId(), e.getCategory(), e.getDesignation(),
        e.getJoiningDate() == null ? null : e.getJoiningDate().toString(),
        salary, e.getBankAccount(), e.getIfsc(), e.getBankName(), e.getPan(),
        e.getDocuments(), e.getComponents(),
        e.getShiftId(), e.getHolidayPolicyId(), e.getLeavePolicyId());
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
