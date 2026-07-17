package com.hitech.erp.project.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "projects")
public class ProjectEntity extends BaseEntity {

  @Column(name = "project_code", length = 50)
  private String projectCode;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(length = 200)
  private String category;

  @Column(length = 100)
  private String stage;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProjectStatus status = ProjectStatus.NOT_STARTED;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProjectHealth health = ProjectHealth.HEALTHY;

  @Column(name = "customer_name", length = 200)
  private String customerName;

  @Column(name = "key_personnel", length = 200)
  private String keyPersonnel;

  @Column(length = 500)
  private String address;

  @Column(length = 100)
  private String city;

  @Column(name = "company_branch", length = 200)
  private String companyBranch;

  @Column(name = "start_date", length = 30)
  private String startDate;

  @Column(name = "end_date", length = 30)
  private String endDate;

  @Column(nullable = false)
  private int progress = 0;

  @Column(name = "attendance_radius", nullable = false)
  private int attendanceRadius = 500;

  @Column(name = "project_value", nullable = false)
  private double projectValue = 0;

  @Column(length = 100)
  private String orientation;

  @Column(length = 100)
  private String dimension;

  @Column(name = "scope_of_work", length = 2000)
  private String scopeOfWork;

  // Running financial + workload aggregates surfaced on the frontend Project model.
  @Column(name = "in_amount", nullable = false)
  private double inAmount = 0;

  @Column(name = "out_amount", nullable = false)
  private double outAmount = 0;

  @Column(name = "todo_count", nullable = false)
  private int todoCount = 0;
}
