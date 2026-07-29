package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One member's attendance for one calendar date. Unique on (user_id, date). Punch-in and
 * punch-out both update the same row. Codes: P/A/HD/PL/WO/NM (Present/Absent/Half Day/Paid
 * Leave/Week Off/Not Marked).
 */
@Getter
@Setter
@Entity
@Table(
    name = "payroll_attendance",
    uniqueConstraints = @UniqueConstraint(name = "uq_payroll_attendance_user_date", columnNames = {"user_id", "date"}))
public class AttendanceEntity extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false)
  private LocalDate date;

  @Column(nullable = false, length = 4)
  private String code = "NM";

  @Column(name = "in_time", length = 5)
  private String inTime;

  @Column(name = "out_time", length = 5)
  private String outTime;

  @Column(name = "overtime_hours", nullable = false, precision = 5, scale = 2)
  private BigDecimal overtimeHours = BigDecimal.ZERO;

  @Column(name = "fine_hours", nullable = false, precision = 5, scale = 2)
  private BigDecimal fineHours = BigDecimal.ZERO;

  @Column(name = "project_id")
  private Long projectId;

  @Column(name = "punch_in_lat", precision = 10, scale = 6)
  private BigDecimal punchInLat;

  @Column(name = "punch_in_lng", precision = 10, scale = 6)
  private BigDecimal punchInLng;

  @Column(name = "punch_out_lat", precision = 10, scale = 6)
  private BigDecimal punchOutLat;

  @Column(name = "punch_out_lng", precision = 10, scale = 6)
  private BigDecimal punchOutLng;

  @Column(name = "face_score_in", precision = 4, scale = 3)
  private BigDecimal faceScoreIn;

  @Column(name = "face_score_out", precision = 4, scale = 3)
  private BigDecimal faceScoreOut;

  @Column(name = "punch_in_photo", columnDefinition = "TEXT")
  private String punchInPhoto;

  @Column(name = "punch_out_photo", columnDefinition = "TEXT")
  private String punchOutPhoto;
}
