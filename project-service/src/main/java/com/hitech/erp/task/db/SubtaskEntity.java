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

@Getter
@Setter
@Entity
@Table(name = "task_subtasks")
public class SubtaskEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "task_id", nullable = false)
  private TaskEntity task;

  @Column(nullable = false, length = 500)
  private String title;

  @Column(nullable = false)
  private boolean done = false;

  @Column(name = "assignee_id")
  private Long assigneeId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 0;
}
