package com.hitech.erp.task.service;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.task.db.SubtaskEntity;
import com.hitech.erp.task.db.TaskAttachmentEntity;
import com.hitech.erp.task.db.TaskCommentEntity;
import com.hitech.erp.task.db.TaskEntity;
import com.hitech.erp.task.db.TaskPriority;
import com.hitech.erp.task.db.TaskRepository;
import com.hitech.erp.task.db.TaskStatus;
import com.hitech.erp.task.dto.TaskDtos.AttachmentInput;
import com.hitech.erp.task.dto.TaskDtos.CommentInput;
import com.hitech.erp.task.dto.TaskDtos.SubtaskInput;
import com.hitech.erp.task.dto.TaskDtos.TaskPatchRequest;
import com.hitech.erp.task.dto.TaskDtos.TaskResponse;
import com.hitech.erp.task.dto.TaskDtos.TaskUpsertRequest;
import com.hitech.erp.task.mapper.TaskMapper;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {

  private final TaskRepository taskRepository;
  private final TaskMapper mapper;

  // ---- Listing ----
  // Tasks are NOT project-membership scoped: anyone with TASKOPAD:VIEW sees every task (there is no
  // "only my tasks" restriction). Only the Projects module is membership-scoped. An optional
  // projectId narrows the list (used by the project-embedded view and the header project dropdown).

  @Transactional(readOnly = true)
  public List<TaskResponse> list(AuthenticatedUser user, Long projectId) {
    List<TaskEntity> tasks = taskRepository.findAllByOrderByCreatedAtDesc();
    if (projectId != null) {
      tasks = tasks.stream().filter(t -> projectId.equals(t.getProjectId())).toList();
    }
    return mapper.toResponses(tasks);
  }

  @Transactional(readOnly = true)
  public TaskResponse get(AuthenticatedUser user, Long id) {
    return mapper.toResponse(requireTask(id));
  }

  // ---- Create / update ----

  @Transactional
  public TaskResponse create(AuthenticatedUser user, TaskUpsertRequest r) {
    TaskEntity t = new TaskEntity();
    t.setCode(nextCode());
    t.setCreatedBy(user.id());
    apply(t, r);
    t.logActivity(user.id(), "Task created");
    return mapper.toResponse(taskRepository.save(t));
  }

  @Transactional
  public TaskResponse update(AuthenticatedUser user, Long id, TaskUpsertRequest r) {
    TaskEntity t = requireTask(id);
    apply(t, r);
    t.logActivity(user.id(), "Task updated");
    return mapper.toResponse(taskRepository.save(t));
  }

  /** Inline patch from the list/main view — status, priority and/or progress only. */
  @Transactional
  public TaskResponse patch(AuthenticatedUser user, Long id, TaskPatchRequest r) {
    TaskEntity t = requireTask(id);

    if (r.status() != null) {
      TaskStatus status = TaskStatus.from(r.status());
      if (status != t.getStatus()) {
        t.setStatus(status);
        if (status == TaskStatus.COMPLETED) t.setProgress(100);
        t.logActivity(user.id(), "Status changed to " + label(status));
      }
    }
    if (r.priority() != null) {
      TaskPriority priority = TaskPriority.from(r.priority());
      if (priority != t.getPriority()) {
        t.setPriority(priority);
        t.logActivity(user.id(), "Priority changed to " + label(priority));
      }
    }
    if (r.progress() != null) {
      int p = clampProgress(r.progress());
      if (p != t.getProgress()) {
        t.setProgress(p);
        if (p == 100 && t.getStatus() != TaskStatus.COMPLETED) {
          t.setStatus(TaskStatus.COMPLETED);
        }
        t.logActivity(user.id(), "Progress set to " + p + "%");
      }
    }
    return mapper.toResponse(taskRepository.save(t));
  }

  @Transactional
  public void delete(AuthenticatedUser user, Long id) {
    taskRepository.delete(requireTask(id));
  }

  // ---- Sub-resources ----

  @Transactional
  public TaskResponse addComment(AuthenticatedUser user, Long id, CommentInput input) {
    TaskEntity t = requireTask(id);
    TaskCommentEntity c = new TaskCommentEntity();
    c.setTask(t);
    c.setAuthorId(user.id());
    c.setText(input.text().trim());
    t.getComments().add(c);
    t.logActivity(user.id(), "Added a comment");
    return mapper.toResponse(taskRepository.save(t));
  }

  @Transactional
  public TaskResponse addAttachment(AuthenticatedUser user, Long id, AttachmentInput input) {
    TaskEntity t = requireTask(id);
    TaskAttachmentEntity a = new TaskAttachmentEntity();
    a.setTask(t);
    a.setUploadedBy(user.id());
    a.setName(input.name().trim());
    a.setSizeLabel(input.sizeLabel());
    a.setContentType(input.contentType());
    a.setDataUrl(input.dataUrl());
    t.getAttachments().add(a);
    t.logActivity(user.id(), "Attached " + a.getName());
    return mapper.toResponse(taskRepository.save(t));
  }

  @Transactional
  public TaskResponse toggleSubtask(AuthenticatedUser user, Long id, Long subtaskId) {
    TaskEntity t = requireTask(id);
    t.getSubtasks().stream()
        .filter(s -> s.getId().equals(subtaskId))
        .findFirst()
        .ifPresent(s -> s.setDone(!s.isDone()));
    return mapper.toResponse(taskRepository.save(t));
  }

  // ---- Internals ----

  private void apply(TaskEntity t, TaskUpsertRequest r) {
    t.setTitle(r.title().trim());
    t.setDescription(r.description());
    t.setProjectId(r.projectId());
    t.setAssigneeId(r.assigneeId());
    t.setClientName(r.clientName());
    if (r.status() != null) t.setStatus(TaskStatus.from(r.status()));
    if (r.priority() != null) t.setPriority(TaskPriority.from(r.priority()));
    if (r.progress() != null) t.setProgress(clampProgress(r.progress()));
    t.setDueDate(r.dueDate());
    t.setDraft(Boolean.TRUE.equals(r.draft()));

    // Completing/filling keeps status and progress coherent.
    if (t.getStatus() == TaskStatus.COMPLETED) t.setProgress(100);
    else if (t.getProgress() == 100) t.setStatus(TaskStatus.COMPLETED);

    t.getFollowerIds().clear();
    if (r.followerIds() != null) t.getFollowerIds().addAll(new LinkedHashSet<>(r.followerIds()));

    replaceSubtasks(t, r.subtasks());
  }

  private void replaceSubtasks(TaskEntity t, List<SubtaskInput> inputs) {
    t.getSubtasks().clear();
    if (inputs == null) return;
    int order = 0;
    for (SubtaskInput in : inputs) {
      if (in.title() == null || in.title().isBlank()) continue;
      SubtaskEntity s = new SubtaskEntity();
      s.setTask(t);
      s.setTitle(in.title().trim());
      s.setDone(in.done());
      s.setAssigneeId(in.assigneeId());
      s.setSortOrder(order++);
      t.getSubtasks().add(s);
    }
  }

  private TaskEntity requireTask(Long id) {
    return taskRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Task not found: " + id));
  }

  private int clampProgress(int p) {
    return Math.max(0, Math.min(100, p));
  }

  /** Sequential human code T-1001, T-1002, … based on current count. */
  private String nextCode() {
    return "T-" + (1001 + taskRepository.count());
  }

  private String label(TaskStatus s) {
    return switch (s) {
      case PENDING -> "Pending";
      case IN_PROGRESS -> "In Progress";
      case ON_HOLD -> "On Hold";
      case STUCK -> "Stuck";
      case COMPLETED -> "Completed";
    };
  }

  private String label(TaskPriority p) {
    return switch (p) {
      case LOW -> "Low";
      case MEDIUM -> "Medium";
      case HIGH -> "High";
    };
  }
}
