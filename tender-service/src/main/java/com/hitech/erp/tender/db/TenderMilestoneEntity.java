package com.hitech.erp.tender.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Status-tracker record for a tender's milestone checklist. The set of steps is user-editable at
 * runtime, so per-step completion is kept as a JSON object ({@code {"analysis":true,...}}) rather
 * than fixed columns.
 */
@Getter
@Setter
@Entity
@Table(name = "tender_milestones")
public class TenderMilestoneEntity extends BaseEntity {

  /** The tender's portal/GeM id this checklist belongs to (matches TenderEntity.tenderId). */
  @Column(name = "tender_ref", length = 120)
  private String tenderRef;

  @Column(name = "name_of_work", length = 2000)
  private String nameOfWork;

  @Column(name = "work_start_date", length = 30)
  private String workStartDate;

  @Column(length = 200)
  private String progress;

  /** Per-step completion, keyed by the tracker step key. JSON object. */
  @Column(name = "steps_json", columnDefinition = "text")
  private String stepsJson;
}
