package com.hitech.erp.payroll.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** Request/response shapes for the Payroll module (setup policies + member payroll profiles). */
public final class PayrollDtos {

  private PayrollDtos() {}

  // ---- Shifts ----
  public record ShiftResponse(
      Long id,
      String name,
      String startTime,
      String endTime,
      List<Integer> weeklyOffs,
      int graceMinutes,
      double halfDayHours,
      double fullDayHours,
      boolean overtimeEnabled) {}

  public record ShiftRequest(
      @NotBlank String name,
      @NotBlank String startTime,
      @NotBlank String endTime,
      List<Integer> weeklyOffs,
      int graceMinutes,
      double halfDayHours,
      double fullDayHours,
      boolean overtimeEnabled) {}

  // ---- Holiday Policy ----
  public record HolidayResponse(String date, String name, String type) {}

  public record HolidayRequest(@NotBlank String date, @NotBlank String name, String type) {}

  public record HolidayPolicyResponse(Long id, String name, int year, List<HolidayResponse> holidays) {}

  public record HolidayPolicyRequest(
      @NotBlank String name, int year, @Valid List<HolidayRequest> holidays) {}

  // ---- Leave Policy ----
  public record LeaveTypeResponse(String name, int annualCount, String accrual, boolean paid) {}

  public record LeaveTypeRequest(
      @NotBlank String name, int annualCount, String accrual, boolean paid) {}

  public record LeavePolicyResponse(Long id, String name, String cycle, List<LeaveTypeResponse> types) {}

  public record LeavePolicyRequest(
      @NotBlank String name, String cycle, @Valid List<LeaveTypeRequest> types) {}

  // ---- Payroll Profile ----
  public record SalaryStructure(
      BigDecimal monthlyCtc,
      BigDecimal basic,
      BigDecimal hra,
      BigDecimal otherAllowances,
      String workType,
      BigDecimal workRate,
      boolean pf,
      boolean esic,
      boolean pt) {}

  public record PayrollProfileResponse(
      Long userId,
      String category,
      String designation,
      String joiningDate,
      SalaryStructure salary,
      String bankAccount,
      String ifsc,
      String bankName,
      String pan,
      Long shiftId,
      Long holidayPolicyId,
      Long leavePolicyId) {}

  public record PayrollProfileRequest(
      @NotNull Long userId,
      @NotBlank String category,
      String designation,
      String joiningDate,
      @Valid SalaryStructure salary,
      String bankAccount,
      String ifsc,
      String bankName,
      String pan,
      Long shiftId,
      Long holidayPolicyId,
      Long leavePolicyId) {}

  // ---- Attendance ----
  public record AttendanceResponse(
      Long id,
      Long userId,
      String memberName,
      String date,
      String code,
      String inTime,
      String outTime,
      java.math.BigDecimal overtimeHours,
      java.math.BigDecimal fineHours,
      Long projectId,
      java.math.BigDecimal punchInLat,
      java.math.BigDecimal punchInLng,
      java.math.BigDecimal punchOutLat,
      java.math.BigDecimal punchOutLng,
      java.math.BigDecimal faceScoreIn,
      java.math.BigDecimal faceScoreOut,
      String punchInPhoto,
      String punchOutPhoto) {}

  /** Admin edit of an attendance row (mark absent, set OT, adjust times). */
  public record AttendanceEditRequest(
      @NotNull Long userId,
      @NotBlank String date,
      String code,
      String inTime,
      String outTime,
      java.math.BigDecimal overtimeHours,
      java.math.BigDecimal fineHours,
      Long projectId) {}

  /** Self-service punch — the caller punches themselves in or out. Only lat/lng/faceScore + optional
   *  projectId come from the client; the user id is inferred from the JWT server-side. */
  public record PunchRequest(
      @NotBlank String direction,   // "IN" or "OUT"
      java.math.BigDecimal lat,
      java.math.BigDecimal lng,
      java.math.BigDecimal faceScore,
      Long projectId,
      String photo) {}            // small base64 JPEG selfie captured at punch time

  /** Self-service face enrolment — the member registers their reference faceprint + selfie. */
  public record FaceEnrollmentRequest(
      @NotNull List<Double> descriptor,
      String photo) {}

  /** The member's enrolled face (descriptor + selfie), or enrolled=false when none is set yet. */
  public record FaceEnrollmentResponse(
      List<Double> descriptor,
      String photo,
      boolean enrolled) {}

  // ---- Work locations (geofences) ----
  public record GeoPoint(double lat, double lng) {}

  public record LocationRequest(
      @NotBlank String name,
      @NotNull List<GeoPoint> points,
      List<Long> memberIds,
      Long projectId) {}

  public record LocationResponse(
      Long id,
      String name,
      List<GeoPoint> points,
      List<Long> memberIds,
      Long projectId,
      String projectName) {}

  // ---- Leave Requests ----
  public record LeaveRequestResponse(
      Long id,
      Long userId,
      String memberName,
      String leaveTypeName,
      String fromDate,
      String toDate,
      java.math.BigDecimal days,
      String reason,
      String status,
      Long approverId,
      String approverName,
      String approvedAt,
      String decisionNote,
      String createdAt) {}

  /** Member applies for leave — user id inferred from JWT server-side. */
  public record LeaveApplyRequest(
      @NotBlank String leaveTypeName,
      @NotBlank String fromDate,
      @NotBlank String toDate,
      String reason) {}

  /** Approver decision — approve or reject a pending request, with an optional note. */
  public record LeaveDecisionRequest(
      @NotBlank String action,   // "APPROVE" or "REJECT"
      String note) {}

  /** How many leave days a member has used vs their assigned policy — for the "My Leave" screen. */
  public record LeaveBalanceResponse(
      String leaveTypeName,
      int annualCount,
      java.math.BigDecimal taken,
      java.math.BigDecimal remaining,
      boolean paid) {}

  // ---- Loans ----
  public record LoanResponse(
      Long id,
      Long userId,
      String memberName,
      String name,
      String description,
      java.math.BigDecimal principal,
      int tenureMonths,
      java.math.BigDecimal annualRate,
      String interestType,
      String disbursementDate,
      String startMonth,
      java.math.BigDecimal emi,
      java.math.BigDecimal outstanding) {}

  public record LoanRequest(
      @NotNull Long userId,
      @NotBlank String name,
      String description,
      @NotNull java.math.BigDecimal principal,
      int tenureMonths,
      java.math.BigDecimal annualRate,
      String interestType,
      @NotBlank String disbursementDate,
      @NotBlank String startMonth,
      @NotNull java.math.BigDecimal emi,
      java.math.BigDecimal outstanding) {}

  // ---- Reimbursements ----
  public record ReimbursementResponse(
      Long id,
      Long userId,
      String memberName,
      String expenseType,
      String claimId,
      String expenseDate,
      String appliedAt,
      String approvedAt,
      String settlementDate,
      java.math.BigDecimal requestedAmount,
      java.math.BigDecimal approvedAmount,
      Long approverId,
      String approverName,
      String status) {}

  public record ReimbursementCreateRequest(
      Long userId,           // null = signed-in member (self-service)
      @NotBlank String expenseType,
      String claimId,
      @NotBlank String expenseDate,
      @NotNull java.math.BigDecimal requestedAmount) {}

  public record ReimbursementDecisionRequest(
      @NotBlank String action,   // "APPROVE" / "REJECT" / "PAY"
      java.math.BigDecimal approvedAmount) {}

  // ---- Payroll Runs & Payslips ----
  public record PayslipResponse(
      Long id,
      Long userId,
      String memberName,
      java.math.BigDecimal gross,
      java.math.BigDecimal pf,
      java.math.BigDecimal esic,
      java.math.BigDecimal pt,
      java.math.BigDecimal loanEmi,
      java.math.BigDecimal reimbursements,
      java.math.BigDecimal net,
      java.math.BigDecimal payableDays,
      int totalDays,
      String month) {}

  public record PayrollRunResponse(
      Long id,
      String month,
      String status,
      java.math.BigDecimal totalGross,
      java.math.BigDecimal totalNet,
      int personCount,
      Long lockedBy,
      String lockedByName,
      String lockedAt,
      String createdAt,
      String paidAt,
      String paidByName,
      List<PayslipResponse> payslips) {}

  public record PayrollRunSummary(
      Long id,
      String month,
      String status,
      java.math.BigDecimal totalGross,
      java.math.BigDecimal totalNet,
      int personCount,
      String createdAt,
      String paidAt) {}
}
