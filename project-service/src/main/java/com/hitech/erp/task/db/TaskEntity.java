package com.hitech.erp.task.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tasks")
public class TaskEntity extends BaseEntity {

  @Column(name = "task_code", nullable = false, length = 40)
  private String code;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(columnDefinition = "text")
  private String description;

  /** project-service project id; null = unassigned to a project. */
  @Column(name = "project_id")
  private Long projectId;

  @Column(name = "assignee_id", nullable = false)
  private Long assigneeId;

  /** app_users id of the creator — used for access ("tasks I created"). */
  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "client_name", length = 200)
  private String clientName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TaskStatus status = TaskStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TaskPriority priority = TaskPriority.LOW;

  @Column(nullable = false)
  private int progress = 0;

  /** ISO yyyy-MM-dd, matching the project module's string-date convention. */
  @Column(name = "due_date", length = 30)
  private String dueDate;

  @Column(name = "is_draft", nullable = false)
  private boolean draft = false;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "task_followers", joinColumns = @JoinColumn(name = "task_id"))
  @Column(name = "user_id", nullable = false)
  private Set<Long> followerIds = new LinkedHashSet<>();

  @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC, id ASC")
  private List<SubtaskEntity> subtasks = new ArrayList<>();

  @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("createdAt ASC, id ASC")
  private List<TaskCommentEntity> comments = new ArrayList<>();

  @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("createdAt ASC, id ASC")
  private List<TaskAttachmentEntity> attachments = new ArrayList<>();

  @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("createdAt DESC, id DESC")
  private List<TaskActivityEntity> activity = new ArrayList<>();

  public void logActivity(Long actorId, String text) {
    TaskActivityEntity a = new TaskActivityEntity();
    a.setTask(this);
    a.setActorId(actorId);
    a.setText(text);
    this.activity.add(a);
  }
}
