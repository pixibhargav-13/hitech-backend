package com.hitech.erp.task.service;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.task.db.SubtaskEntity;
import com.hitech.erp.task.db.TaskAttachmentEntity;
import com.hitech.erp.task.db.TaskCommentEntity;
import com.hitech.erp.task.db.TaskEntity;
import com.hitech.erp.task.db.RecurrenceRule;
import com.hitech.erp.task.db.TaskPriority;
import com.hitech.erp.task.db.TaskRepository;
import com.hitech.erp.task.db.TaskStatus;
import com.hitech.erp.task.dto.TaskDtos.AttachmentInput;
import com.hitech.erp.task.dto.TaskDtos.BulkDeleteRequest;
import com.hitech.erp.task.dto.TaskDtos.BulkPatchRequest;
import com.hitech.erp.task.dto.TaskDtos.CommentInput;
import com.hitech.erp.task.dto.TaskDtos.SubtaskInput;
import com.hitech.erp.task.dto.TaskDtos.TaskPatchRequest;
import com.hitech.erp.task.dto.TaskDtos.TaskResponse;
import com.hitech.erp.task.dto.TaskDtos.TaskUpsertRequest;
import com.hitech.erp.task.mapper.TaskMapper;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import java.time.LocalDate;
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
  private final AppUserRepository userRepository;

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
    boolean wasCompleted = t.getStatus() == TaskStatus.COMPLETED;
    apply(t, r);
    t.logActivity(user.id(), "Task updated");
    TaskEntity saved = taskRepository.save(t);
    // Completing a repeating task from the drawer should roll the series forward too.
    if (!wasCompleted && saved.getStatus() == TaskStatus.COMPLETED) spawnNextOccurrence(user, saved);
    return mapper.toResponse(saved);
  }

  /** Inline patch from the list/main view — status, priority and/or progress only. */
  @Transactional
  public TaskResponse patch(AuthenticatedUser user, Long id, TaskPatchRequest r) {
    TaskEntity t = requireTask(id);

    boolean completedNow = false;
    if (r.status() != null) {
      TaskStatus status = TaskStatus.from(r.status());
      if (status != t.getStatus()) {
        completedNow = status == TaskStatus.COMPLETED;
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
    if (r.pinned() != null && r.pinned() != t.isPinned()) {
      t.setPinned(r.pinned());
      t.logActivity(user.id(), r.pinned() ? "Pinned the task" : "Unpinned the task");
    }
    if (r.reminderAt() != null) {
      String value = r.reminderAt().isBlank() ? null : r.reminderAt();
      t.setReminderAt(value);
      t.logActivity(user.id(), value == null ? "Reminder cleared" : "Reminder set for " + value);
    }
    TaskEntity saved = taskRepository.save(t);
    if (completedNow) spawnNextOccurrence(user, saved);
    return mapper.toResponse(saved);
  }

  /**
   * When a repeating task is completed, create the next occurrence rather than leaving the series
   * dangling. Keeps exactly one open task per series, which is how task apps normally behave.
   * Returns the new task, or null when the task doesn't repeat / the series has ended.
   */
  private TaskEntity spawnNextOccurrence(AuthenticatedUser user, TaskEntity done) {
    if (done.getRecurrenceRule() == null || done.getRecurrenceRule() == RecurrenceRule.NONE) return null;

    LocalDate base = parseDate(done.getDueDate());
    if (base == null) base = LocalDate.now();
    LocalDate next = done.getRecurrenceRule().next(base, done.getRecurrenceInterval());

    LocalDate until = parseDate(done.getRecurrenceUntil());
    if (until != null && next.isAfter(until)) {
      done.logActivity(user.id(), "Recurring series finished");
      taskRepository.save(done);
      return null;
    }

    Long seriesId = done.getSeriesId() != null ? done.getSeriesId() : done.getId();
    if (done.getSeriesId() == null) {
      done.setSeriesId(seriesId);
      taskRepository.save(done);
    }

    TaskEntity copy = new TaskEntity();
    copy.setCode(nextCode());
    copy.setTitle(done.getTitle());
    copy.setDescription(done.getDescription());
    copy.setProjectId(done.getProjectId());
    copy.setAssigneeId(done.getAssigneeId());
    copy.setCreatedBy(user.id());
    copy.setClientName(done.getClientName());
    copy.setStatus(TaskStatus.PENDING);
    copy.setPriority(done.getPriority());
    copy.setProgress(0);
    copy.setDueDate(next.toString());
    copy.setDraft(false);
    copy.setPinned(done.isPinned());
    copy.setRecurrenceRule(done.getRecurrenceRule());
    copy.setRecurrenceInterval(done.getRecurrenceInterval());
    copy.setRecurrenceUntil(done.getRecurrenceUntil());
    // A repeating task's reminder is a time of day, so it applies to every occurrence — carry it
    // across, otherwise the reminder silently disappears after the first one is completed.
    copy.setReminderAt(done.getReminderAt());
    copy.setSeriesId(seriesId);
    copy.setDepartmentId(done.getDepartmentId());
    copy.getFollowerIds().addAll(done.getFollowerIds());
    // Subtasks carry over as a fresh, unchecked checklist.
    int order = 0;
    for (SubtaskEntity s : done.getSubtasks()) {
      SubtaskEntity fresh = new SubtaskEntity();
      fresh.setTitle(s.getTitle());
      fresh.setDone(false);
      fresh.setAssigneeId(s.getAssigneeId());
      fresh.setSortOrder(order++);
      fresh.setTask(copy);
      copy.getSubtasks().add(fresh);
    }

    TaskEntity created = taskRepository.save(copy);
    created.logActivity(user.id(), "Created automatically from a recurring task");
    taskRepository.save(created);
    return created;
  }

  /** The department the given user sits in, or null when they have none. */
  private Long departmentOf(Long userId) {
    if (userId == null) return null;
    return userRepository
        .findById(userId)
        .map(u -> u.getDepartment() == null ? null : u.getDepartment().getId())
        .orElse(null);
  }

  private static LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
    } catch (Exception ex) {
      return null;
    }
  }

  /** Apply one change across many tasks (bulk actions from the list). */
  @Transactional
  public List<TaskResponse> bulkPatch(AuthenticatedUser user, BulkPatchRequest r) {
    List<TaskEntity> tasks = taskRepository.findAllById(r.taskIds());
    List<TaskEntity> completedNow = new ArrayList<>();
    for (TaskEntity t : tasks) {
      if (r.status() != null) {
        TaskStatus status = TaskStatus.from(r.status());
        if (status == TaskStatus.COMPLETED && t.getStatus() != TaskStatus.COMPLETED) completedNow.add(t);
        t.setStatus(status);
        if (status == TaskStatus.COMPLETED) t.setProgress(100);
        t.logActivity(user.id(), "Status changed to " + label(status) + " (bulk)");
      }
      if (r.priority() != null) {
        t.setPriority(TaskPriority.from(r.priority()));
        t.logActivity(user.id(), "Priority changed (bulk)");
      }
      if (r.pinned() != null) t.setPinned(r.pinned());
      if (r.assigneeId() != null) {
        t.setAssigneeId(r.assigneeId());
        t.logActivity(user.id(), "Reassigned (bulk)");
      }
    }
    List<TaskEntity> saved = taskRepository.saveAll(tasks);
    for (TaskEntity t : completedNow) spawnNextOccurrence(user, t);
    return mapper.toResponses(saved);
  }

  @Transactional
  public void bulkDelete(AuthenticatedUser user, BulkDeleteRequest r) {
    taskRepository.deleteAllById(r.taskIds());
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
    if (r.pinned() != null) t.setPinned(r.pinned());
    if (r.reminderAt() != null) t.setReminderAt(r.reminderAt().isBlank() ? null : r.reminderAt());
    if (r.recurrenceRule() != null) t.setRecurrenceRule(RecurrenceRule.from(r.recurrenceRule()));
    if (r.recurrenceInterval() != null) t.setRecurrenceInterval(Math.max(1, r.recurrenceInterval()));
    if (r.recurrenceUntil() != null) {
      t.setRecurrenceUntil(r.recurrenceUntil().isBlank() ? null : r.recurrenceUntil());
    }
    // Explicit department wins; otherwise inherit whichever team the assignee belongs to.
    if (r.departmentId() != null) {
      t.setDepartmentId(r.departmentId() == 0L ? null : r.departmentId());
    } else if (t.getDepartmentId() == null) {
      t.setDepartmentId(departmentOf(t.getAssigneeId()));
    }

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
