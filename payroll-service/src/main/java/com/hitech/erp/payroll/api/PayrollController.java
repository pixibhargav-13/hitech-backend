package com.hitech.erp.payroll.api;

import com.hitech.erp.payroll.dto.PayrollDtos.AttendanceEditRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.AttendanceResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.FaceEnrollmentRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.FaceEnrollmentResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.LocationRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.LocationResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.LeaveApplyRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.LeaveBalanceResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.LeaveDecisionRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.LeaveRequestResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.LoanRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.LoanResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.PayrollRunResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.PayrollRunSummary;
import com.hitech.erp.payroll.dto.PayrollDtos.PayslipResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.ReimbursementCreateRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.ReimbursementDecisionRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.ReimbursementResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.PunchRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.HolidayPolicyRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.HolidayPolicyResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.LeavePolicyRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.LeavePolicyResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.PayrollProfileRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.PayrollProfileResponse;
import com.hitech.erp.payroll.dto.PayrollDtos.ShiftRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.ShiftResponse;
import com.hitech.erp.payroll.service.AttendanceService;
import com.hitech.erp.payroll.service.LeaveService;
import com.hitech.erp.payroll.service.LoanReimbursementService;
import com.hitech.erp.payroll.service.LocationService;
import com.hitech.erp.payroll.service.PayrollRunService;
import com.hitech.erp.payroll.service.PayrollService;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST surface for Payroll setup policies (shifts, holiday/leave policies) and member profiles. */
@RestController
@RequestMapping("/api/v1/payroll")
@RequiredArgsConstructor
public class PayrollController {

  private final PayrollService service;
  private final AttendanceService attendanceService;
  private final LeaveService leaveService;
  private final LoanReimbursementService loanReimbService;
  private final PayrollRunService runService;
  private final LocationService locationService;

  /** Spring EL for "holds any Payroll manage authority" — an admin/HR/manager, not a plain viewer. */
  private static final String MANAGE =
      "hasAnyAuthority('PAYROLL:CREATE','PAYROLL:EDIT','PAYROLL:DELETE','PAYROLL:APPROVE')";

  private static Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) ? u.id() : null;
  }

  /** True when the signed-in user holds any Payroll manage authority (mirrors {@link #MANAGE}). */
  private static boolean isManager() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return false;
    for (GrantedAuthority a : auth.getAuthorities()) {
      String s = a.getAuthority();
      if ("PAYROLL:CREATE".equals(s) || "PAYROLL:EDIT".equals(s)
          || "PAYROLL:DELETE".equals(s) || "PAYROLL:APPROVE".equals(s)) {
        return true;
      }
    }
    return false;
  }

  /** Guard a per-member read: a non-manager (self-service) user may only read their own records. */
  private static void requireSelfOrManager(Long userId) {
    if (!isManager() && (userId == null || !userId.equals(currentUserId()))) {
      throw new AccessDeniedException("You can only view your own payroll records");
    }
  }

  // ---- Shifts ----
  @GetMapping("/shifts")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<List<ShiftResponse>> getShifts() {
    return ResponseEntity.ok(service.getShifts());
  }

  @PostMapping("/shifts")
  @PreAuthorize("hasAuthority('PAYROLL:CREATE')")
  public ResponseEntity<ShiftResponse> createShift(@Valid @RequestBody ShiftRequest r) {
    return ResponseEntity.ok(service.createShift(r));
  }

  @PutMapping("/shifts/{id}")
  @PreAuthorize("hasAuthority('PAYROLL:EDIT')")
  public ResponseEntity<ShiftResponse> updateShift(@PathVariable("id") Long id, @Valid @RequestBody ShiftRequest r) {
    return ResponseEntity.ok(service.updateShift(id, r));
  }

  @DeleteMapping("/shifts/{id}")
  @PreAuthorize("hasAuthority('PAYROLL:DELETE')")
  public ResponseEntity<Void> deleteShift(@PathVariable("id") Long id) {
    service.deleteShift(id);
    return ResponseEntity.noContent().build();
  }

  // ---- Holiday Policies ----
  @GetMapping("/holiday-policies")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<List<HolidayPolicyResponse>> getHolidayPolicies() {
    return ResponseEntity.ok(service.getHolidayPolicies());
  }

  @PostMapping("/holiday-policies")
  @PreAuthorize("hasAuthority('PAYROLL:CREATE')")
  public ResponseEntity<HolidayPolicyResponse> createHolidayPolicy(@Valid @RequestBody HolidayPolicyRequest r) {
    return ResponseEntity.ok(service.createHolidayPolicy(r));
  }

  @PutMapping("/holiday-policies/{id}")
  @PreAuthorize("hasAuthority('PAYROLL:EDIT')")
  public ResponseEntity<HolidayPolicyResponse> updateHolidayPolicy(@PathVariable("id") Long id, @Valid @RequestBody HolidayPolicyRequest r) {
    return ResponseEntity.ok(service.updateHolidayPolicy(id, r));
  }

  @DeleteMapping("/holiday-policies/{id}")
  @PreAuthorize("hasAuthority('PAYROLL:DELETE')")
  public ResponseEntity<Void> deleteHolidayPolicy(@PathVariable("id") Long id) {
    service.deleteHolidayPolicy(id);
    return ResponseEntity.noContent().build();
  }

  // ---- Leave Policies ----
  @GetMapping("/leave-policies")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<List<LeavePolicyResponse>> getLeavePolicies() {
    return ResponseEntity.ok(service.getLeavePolicies());
  }

  @PostMapping("/leave-policies")
  @PreAuthorize("hasAuthority('PAYROLL:CREATE')")
  public ResponseEntity<LeavePolicyResponse> createLeavePolicy(@Valid @RequestBody LeavePolicyRequest r) {
    return ResponseEntity.ok(service.createLeavePolicy(r));
  }

  @PutMapping("/leave-policies/{id}")
  @PreAuthorize("hasAuthority('PAYROLL:EDIT')")
  public ResponseEntity<LeavePolicyResponse> updateLeavePolicy(@PathVariable("id") Long id, @Valid @RequestBody LeavePolicyRequest r) {
    return ResponseEntity.ok(service.updateLeavePolicy(id, r));
  }

  @DeleteMapping("/leave-policies/{id}")
  @PreAuthorize("hasAuthority('PAYROLL:DELETE')")
  public ResponseEntity<Void> deleteLeavePolicy(@PathVariable("id") Long id) {
    service.deleteLeavePolicy(id);
    return ResponseEntity.noContent().build();
  }

  // ---- Payroll Profiles ----
  @GetMapping("/profiles")
  @PreAuthorize(MANAGE)
  public ResponseEntity<List<PayrollProfileResponse>> getProfiles(
      @RequestParam(name = "userIds", required = false) List<Long> userIds) {
    return ResponseEntity.ok(service.getProfiles(userIds));
  }

  @GetMapping("/profiles/{userId}")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<PayrollProfileResponse> getProfile(@PathVariable("userId") Long userId) {
    requireSelfOrManager(userId);
    return ResponseEntity.ok(service.getProfile(userId));
  }

  /** Create-or-update, keyed by userId in the body — a member has at most one profile. */
  @PostMapping("/profiles")
  @PreAuthorize("hasAuthority('PAYROLL:CREATE') or hasAuthority('PAYROLL:EDIT')")
  public ResponseEntity<PayrollProfileResponse> saveProfile(@Valid @RequestBody PayrollProfileRequest r) {
    return ResponseEntity.ok(service.saveProfile(r));
  }

  @DeleteMapping("/profiles/{userId}")
  @PreAuthorize("hasAuthority('PAYROLL:DELETE')")
  public ResponseEntity<Void> deleteProfile(@PathVariable("userId") Long userId) {
    service.deleteProfile(userId);
    return ResponseEntity.noContent().build();
  }

  // ---- Attendance ----
  /** Self-service punch — the signed-in member punches themselves in/out. */
  @PostMapping("/attendance/punch")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<AttendanceResponse> punch(@Valid @RequestBody PunchRequest r) {
    return ResponseEntity.ok(attendanceService.punch(currentUserId(), r));
  }

  /** Today's attendance for the signed-in member (or a NM stub) — the punch page's initial read. */
  @GetMapping("/attendance/today")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<AttendanceResponse> today() {
    return ResponseEntity.ok(attendanceService.getToday(currentUserId()));
  }

  /** A member's attendance for a date range — for admin edits and self-service history. */
  @GetMapping("/attendance/member/{userId}")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<List<AttendanceResponse>> getForMember(
      @PathVariable("userId") Long userId,
      @RequestParam("from") String from,
      @RequestParam("to") String to) {
    requireSelfOrManager(userId);
    return ResponseEntity.ok(attendanceService.getForMember(userId, LocalDate.parse(from), LocalDate.parse(to)));
  }

  /** Muster roll — every member's attendance across a range, for the admin view. */
  @GetMapping("/attendance/muster")
  @PreAuthorize(MANAGE)
  public ResponseEntity<List<AttendanceResponse>> muster(
      @RequestParam("from") String from,
      @RequestParam("to") String to) {
    return ResponseEntity.ok(attendanceService.getMuster(LocalDate.parse(from), LocalDate.parse(to)));
  }

  /** Attendance filtered to one project — the Project detail's Attendance tab. */
  @GetMapping("/attendance/project/{projectId}")
  @PreAuthorize(MANAGE)
  public ResponseEntity<List<AttendanceResponse>> getForProject(
      @PathVariable("projectId") Long projectId,
      @RequestParam("from") String from,
      @RequestParam("to") String to) {
    return ResponseEntity.ok(attendanceService.getForProject(projectId, LocalDate.parse(from), LocalDate.parse(to)));
  }

  /** Admin edit — mark absent, adjust times, set OT/fine. */
  @PostMapping("/attendance/edit")
  @PreAuthorize("hasAuthority('PAYROLL:EDIT')")
  public ResponseEntity<AttendanceResponse> editAttendance(@Valid @RequestBody AttendanceEditRequest r) {
    return ResponseEntity.ok(attendanceService.edit(r));
  }

  /** Admin housekeeping — delete all attendance rows in a date range (used to reset before testing). */
  @DeleteMapping("/attendance/range")
  @PreAuthorize("hasAuthority('PAYROLL:DELETE')")
  public ResponseEntity<Void> clearAttendance(
      @RequestParam("from") String from, @RequestParam("to") String to) {
    attendanceService.clearRange(LocalDate.parse(from), LocalDate.parse(to));
    return ResponseEntity.noContent().build();
  }

  // ---- Face enrolment (self-service) ----
  /** The signed-in member's enrolled reference face (for the punch page to match against). */
  @GetMapping("/attendance/face")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<FaceEnrollmentResponse> getMyFace() {
    return ResponseEntity.ok(attendanceService.getFace(currentUserId()));
  }

  /** Register (or re-register) the signed-in member's reference faceprint + selfie. */
  @PostMapping("/attendance/face")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<FaceEnrollmentResponse> enrolFace(@Valid @RequestBody FaceEnrollmentRequest r) {
    return ResponseEntity.ok(attendanceService.saveFace(currentUserId(), r));
  }

  // ---- Leave ----
  /** The signed-in member's own leave history. */
  @GetMapping("/leave/mine")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<List<LeaveRequestResponse>> myLeave() {
    return ResponseEntity.ok(leaveService.getForMember(currentUserId()));
  }

  /** The signed-in member's remaining leave per type, from their assigned policy. */
  @GetMapping("/leave/balance")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<List<LeaveBalanceResponse>> myBalance() {
    return ResponseEntity.ok(leaveService.getBalance(currentUserId()));
  }

  /** Someone else's leave history — admin/manager view. */
  @GetMapping("/leave/member/{userId}")
  @PreAuthorize(MANAGE)
  public ResponseEntity<List<LeaveRequestResponse>> memberLeave(@PathVariable("userId") Long userId) {
    return ResponseEntity.ok(leaveService.getForMember(userId));
  }

  /** All pending requests — for the approver queue. */
  @GetMapping("/leave/pending")
  @PreAuthorize("hasAuthority('PAYROLL:APPROVE')")
  public ResponseEntity<List<LeaveRequestResponse>> pending() {
    return ResponseEntity.ok(leaveService.getPending());
  }

  /** Member applies for leave — user id inferred from JWT server-side. */
  @PostMapping("/leave/apply")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<LeaveRequestResponse> applyLeave(@Valid @RequestBody LeaveApplyRequest r) {
    return ResponseEntity.ok(leaveService.apply(currentUserId(), r));
  }

  /** Approve or reject a pending request. */
  @PostMapping("/leave/{id}/decide")
  @PreAuthorize("hasAuthority('PAYROLL:APPROVE')")
  public ResponseEntity<LeaveRequestResponse> decideLeave(
      @PathVariable("id") Long id, @Valid @RequestBody LeaveDecisionRequest r) {
    return ResponseEntity.ok(leaveService.decide(id, currentUserId(), r));
  }

  /** Member cancels their own pending request. */
  @PostMapping("/leave/{id}/cancel")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<LeaveRequestResponse> cancelLeave(@PathVariable("id") Long id) {
    return ResponseEntity.ok(leaveService.cancel(id, currentUserId()));
  }

  // ---- Loans ----
  @GetMapping("/loans")
  @PreAuthorize(MANAGE)
  public ResponseEntity<List<LoanResponse>> getLoans() {
    return ResponseEntity.ok(loanReimbService.getLoans());
  }

  @GetMapping("/loans/mine")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<List<LoanResponse>> myLoans() {
    return ResponseEntity.ok(loanReimbService.getLoansForMember(currentUserId()));
  }

  @PostMapping("/loans")
  @PreAuthorize("hasAuthority('PAYROLL:CREATE')")
  public ResponseEntity<LoanResponse> createLoan(@Valid @RequestBody LoanRequest r) {
    return ResponseEntity.ok(loanReimbService.createLoan(r));
  }

  @PutMapping("/loans/{id}")
  @PreAuthorize("hasAuthority('PAYROLL:EDIT')")
  public ResponseEntity<LoanResponse> updateLoan(@PathVariable("id") Long id, @Valid @RequestBody LoanRequest r) {
    return ResponseEntity.ok(loanReimbService.updateLoan(id, r));
  }

  @DeleteMapping("/loans/{id}")
  @PreAuthorize("hasAuthority('PAYROLL:DELETE')")
  public ResponseEntity<Void> deleteLoan(@PathVariable("id") Long id) {
    loanReimbService.deleteLoan(id);
    return ResponseEntity.noContent().build();
  }

  // ---- Reimbursements ----
  @GetMapping("/reimbursements")
  @PreAuthorize(MANAGE)
  public ResponseEntity<List<ReimbursementResponse>> getReimbursements() {
    return ResponseEntity.ok(loanReimbService.getAll());
  }

  @GetMapping("/reimbursements/mine")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<List<ReimbursementResponse>> myReimbursements() {
    return ResponseEntity.ok(loanReimbService.getForMember(currentUserId()));
  }

  @PostMapping("/reimbursements")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<ReimbursementResponse> createReimbursement(@Valid @RequestBody ReimbursementCreateRequest r) {
    return ResponseEntity.ok(loanReimbService.create(currentUserId(), r));
  }

  @PostMapping("/reimbursements/{id}/decide")
  @PreAuthorize("hasAuthority('PAYROLL:APPROVE')")
  public ResponseEntity<ReimbursementResponse> decideReimbursement(
      @PathVariable("id") Long id, @Valid @RequestBody ReimbursementDecisionRequest r) {
    return ResponseEntity.ok(loanReimbService.decide(id, currentUserId(), r));
  }

  // ---- Payroll Runs & Payslips ----
  @GetMapping("/runs")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<List<PayrollRunSummary>> listRuns() {
    return ResponseEntity.ok(runService.listRuns());
  }

  @GetMapping("/runs/{month}")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<PayrollRunResponse> getRun(@PathVariable("month") String month) {
    return ResponseEntity.ok(runService.getRun(month));
  }

  /** Generate (or regenerate, if DRAFT) all payslips for a month. */
  @PostMapping("/runs/{month}/generate")
  @PreAuthorize("hasAuthority('PAYROLL:CREATE') or hasAuthority('PAYROLL:EDIT')")
  public ResponseEntity<PayrollRunResponse> generateRun(@PathVariable("month") String month) {
    return ResponseEntity.ok(runService.generate(month));
  }

  @PostMapping("/runs/{month}/lock")
  @PreAuthorize("hasAuthority('PAYROLL:APPROVE')")
  public ResponseEntity<PayrollRunResponse> lockRun(@PathVariable("month") String month) {
    return ResponseEntity.ok(runService.lock(month, currentUserId()));
  }

  @PostMapping("/runs/{month}/unlock")
  @PreAuthorize("hasAuthority('PAYROLL:APPROVE')")
  public ResponseEntity<PayrollRunResponse> unlockRun(@PathVariable("month") String month) {
    return ResponseEntity.ok(runService.unlock(month));
  }

  /** Record that a locked run was paid out manually (bank/cash by Hi-Tech) — no money is moved. */
  @PostMapping("/runs/{month}/pay")
  @PreAuthorize("hasAuthority('PAYROLL:APPROVE')")
  public ResponseEntity<PayrollRunResponse> markRunPaid(@PathVariable("month") String month) {
    return ResponseEntity.ok(runService.markPaid(month, currentUserId()));
  }

  /** Self-service — the signed-in member's own payslips across every run. */
  @GetMapping("/payslips/mine")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<List<PayslipResponse>> myPayslips() {
    return ResponseEntity.ok(runService.mySlips(currentUserId()));
  }

  // ---- Work locations (geofences) ----
  @GetMapping("/locations")
  @PreAuthorize(MANAGE)
  public ResponseEntity<List<LocationResponse>> getLocations() {
    return ResponseEntity.ok(locationService.getAll());
  }

  /** The signed-in member's assigned sites — the punch page reads this to enforce the geofence. */
  @GetMapping("/locations/mine")
  @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
  public ResponseEntity<List<LocationResponse>> getMyLocations() {
    return ResponseEntity.ok(locationService.getForMember(currentUserId()));
  }

  @PostMapping("/locations")
  @PreAuthorize("hasAuthority('PAYROLL:CREATE')")
  public ResponseEntity<LocationResponse> createLocation(@Valid @RequestBody LocationRequest r) {
    return ResponseEntity.ok(locationService.create(r));
  }

  @PutMapping("/locations/{id}")
  @PreAuthorize("hasAuthority('PAYROLL:EDIT')")
  public ResponseEntity<LocationResponse> updateLocation(@PathVariable("id") Long id, @Valid @RequestBody LocationRequest r) {
    return ResponseEntity.ok(locationService.update(id, r));
  }

  @DeleteMapping("/locations/{id}")
  @PreAuthorize("hasAuthority('PAYROLL:DELETE')")
  public ResponseEntity<Void> deleteLocation(@PathVariable("id") Long id) {
    locationService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
