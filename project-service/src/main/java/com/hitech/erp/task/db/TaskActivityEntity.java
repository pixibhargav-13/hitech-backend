package com.hitech.erp.task.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Append-only audit trail for a task (created, status changed, comment added, …). */
@Getter
@Setter
@Entity
@Table(name = "task_activity")
public class TaskActivityEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "task_id", nullable = false)
  private TaskEntity task;

  @Column(name = "actor_id")
  private Long actorId;

  @Column(nullable = false, length = 500)
  private String text;
}
