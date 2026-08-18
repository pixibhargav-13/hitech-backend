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
      /** Identity documents as a JSON array of {type, number}, stored/edited on the frontend. */
      String documents,
      /** Salary components (earnings + deductions) as delimited text — see salaryComponents.ts. */
      String components,
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
      /** Identity documents as a JSON array of {type, number}, stored/edited on the frontend. */
      String documents,
      /** Salary components (earnings + deductions) as delimited text — see salaryComponents.ts. */
      String components,
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
      /** Derived from the punch pair against the shift; null when the member never punched out. */
      java.math.BigDecimal workedHours,
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
      String createdAt,
      /** Multi-level chain state, or null when this request never went through one. */
      com.hitech.erp.approval.dto.ApprovalDtos.ApprovalStateResponse approval,
      /** True when the signed-in caller can decide this request right now. Drives the action buttons. */
      boolean canActNow) {}

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
      java.math.BigDecimal otherDeductions,
      String deductionsDetail,
      java.math.BigDecimal loanEmi,
      java.math.BigDecimal reimbursements,
      java.math.BigDecimal net,
      java.math.BigDecimal payableDays,
      int totalDays,
      String month) {}

  /** Manual adjustment to a DRAFT payslip — net recomputes from these. */
  public record PayslipEditRequest(java.math.BigDecimal gross, java.math.BigDecimal otherDeductions) {}

  // ---- Salary component template (org-wide default) ----
  public record SalaryTemplateResponse(String components) {}

  public record SalaryTemplateRequest(String components) {}

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

  // ---- Project rollups (feed the Project workspace) ----

  /**
   * One project's labour position over a date window. Man-days and cost come from attendance rows
   * tagged with the project, so a member who splits a month between two sites is counted on each
   * for the days they actually punched there.
   */
  public record ProjectManpower(
      /** Members assigned to the project, whether or not they punched. */
      int assignedMembers,
      /** Distinct members who actually punched on this project in the window. */
      int activeMembers,
      /** Members present on the most recent day in the window. */
      int presentToday,
      /** Payable days worked on this project (half days count 0.5). */
      BigDecimal manDays,
      BigDecimal overtimeHours,
      /** Man-days × the member's daily rate. Zero for members with no rate on file. */
      BigDecimal labourCost,
      /** True when at least one contributing member has no payroll profile, so cost understates. */
      boolean costIncomplete,
      List<ProjectManpowerDay> trend) {}

  /** One day's headcount on a project — the Dashboard's attendance chart. */
  public record ProjectManpowerDay(String date, int workers, BigDecimal manDays) {}

  /**
   * One member's contribution to a project over the window. Money fields are null unless the caller
   * holds {@code PAYROLL:VIEW} — a site supervisor sees who worked and how much, not what they earn.
   */
  public record ProjectStaffRow(
      Long userId,
      String name,
      String email,
      String phone,
      String staffType,
      String department,
      String roleName,
      String photoUrl,
      String designation,
      String category,
      /** DAILY / HOURLY / PIECE for work-basis members; null for salaried. */
      String workType,
      BigDecimal dailyRate,
      int presentDays,
      int absentDays,
      BigDecimal manDays,
      BigDecimal overtimeHours,
      BigDecimal labourCost,
      String lastSeen) {}
}
