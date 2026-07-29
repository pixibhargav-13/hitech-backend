package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** A member's leave application. Status: PENDING → APPROVED/REJECTED/CANCELLED. */
@Getter
@Setter
@Entity
@Table(name = "payroll_leave_requests")
public class LeaveRequestEntity extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "leave_type_name", nullable = false, length = 120)
  private String leaveTypeName;

  @Column(name = "from_date", nullable = false)
  private LocalDate fromDate;

  @Column(name = "to_date", nullable = false)
  private LocalDate toDate;

  @Column(nullable = false, precision = 4, scale = 1)
  private BigDecimal days;

  @Column(columnDefinition = "TEXT")
  private String reason;

  @Column(nullable = false, length = 15)
  private String status = "PENDING";

  @Column(name = "approver_id")
  private Long approverId;

  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

  @Column(name = "decision_note", columnDefinition = "TEXT")
  private String decisionNote;
}
