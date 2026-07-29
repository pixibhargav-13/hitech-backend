package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A work shift with its attendance rules — weekly-offs, grace period, half/full-day hours, OT. */
@Getter
@Setter
@Entity
@Table(name = "payroll_shifts")
public class ShiftEntity extends BaseEntity {

  @Column(nullable = false, length = 120)
  private String name;

  /** "HH:MM" 24h. */
  @Column(name = "start_time", nullable = false, length = 5)
  private String startTime;

  @Column(name = "end_time", nullable = false, length = 5)
  private String endTime;

  /** CSV of weekday numbers that are weekly-offs, 0=Sunday..6=Saturday, e.g. "0" or "0,6". */
  @Column(name = "weekly_offs", length = 30)
  private String weeklyOffs = "";

  @Column(name = "grace_minutes", nullable = false)
  private int graceMinutes = 0;

  @Column(name = "half_day_hours", nullable = false)
  private double halfDayHours = 4;

  @Column(name = "full_day_hours", nullable = false)
  private double fullDayHours = 8;

  @Column(name = "overtime_enabled", nullable = false)
  private boolean overtimeEnabled = true;
}
