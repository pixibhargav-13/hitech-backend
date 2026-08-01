package com.hitech.erp.task.service;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.project.db.ProjectMemberRepository;
import com.hitech.erp.project.service.AccessService;
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
import com.hitech.erp.usermanagement.db.RoleEntity;
import com.hitech.erp.usermanagement.db.RoleRepository;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {

  private final TaskRepository taskRepository;
  private final TaskMapper mapper;
  private final AppUserRepository userRepository;
  private final AccessService accessService;
  private final RoleRepository roleRepository;
  private final ProjectMemberRepository memberRepository;

  private static final String PROJECT_MANAGER = "Project Manager";

  // ---- Listing ----
  // Two view modes surfaced in the UI as a "My Tasks / All Users' Tasks" toggle:
  //   MINE  = only tasks I'm involved in (assignee, follower, creator).
  //   ALL   = MINE ∪ (tasks assigned to anyone in my role subtree AND in my accessible projects).
  // Super Admin's MINE is still their own involvement; only ALL bypasses to everything. Non-admins
  // with an empty role subtree see the same list either way (the frontend hides the toggle).

  @Transactional(readOnly = true)
  public List<TaskResponse> list(AuthenticatedUser user, Long projectId, String scope) {
    List<TaskEntity> tasks = taskRepository.findAllByOrderByCreatedAtDesc();
    if (projectId != null) {
      tasks = tasks.stream().filter(t -> projectId.equals(t.getProjectId())).toList();
    }
    Long me = user.id();
    boolean allMode = "ALL".equalsIgnoreCase(scope);
    if (accessService.seesEverything(user)) {
      // Super Admin: ALL = everything, MINE = own involvement only.
      if (!allMode) {
        tasks = tasks.stream().filter(t -> involves(me, t)).toList();
      }
      return mapper.toResponses(tasks);
    }
    Set<Long> subtree = allMode ? accessService.subtreeUserIds(user) : Set.of();
    Set<Long> myProjects =
        allMode && !subtree.isEmpty()
            ? new HashSet<>(accessService.accessibleProjectIds(user))
            : Set.of();
    // Office assignees in the subtree bypass the project intersection — their projectId (if any)
    // is metadata, not a scope. Empty when there's no subtree.
    Set<Long> officeInSubtree =
        allMode && !subtree.isEmpty() ? accessService.officeIdsAmong(subtree) : Set.of();
    tasks =
        tasks.stream()
            .filter(
                t ->
                    involves(me, t)
                        || (allMode && underMe(t, subtree, myProjects, officeInSubtree)))
            .toList();
    return mapper.toResponses(tasks);
  }

  @Transactional(readOnly = true)
  public TaskResponse get(AuthenticatedUser user, Long id) {
    TaskEntity t = requireTask(id);
    if (!canSee(user, t) && !canApprove(user, t)) {
      // 404 (not 403) so IDs from another manager's subtree don't leak existence.
      // The approver of a pending completion may open the task even if not otherwise involved.
      throw new EntityNotFoundException("Task not found: " + id);
    }
    return mapper.toResponse(t);
  }

  /**
   * True when the user is allowed to open this task. Super Admin can open every task (needed so
   * their "All Users" mode can drill into any task). Everyone else follows the MINE ∪ ALL rule.
   */
  private boolean canSee(AuthenticatedUser user, TaskEntity t) {
    if (accessService.seesEverything(user)) return true;
    if (involves(user.id(), t)) return true;
    Set<Long> subtree = accessService.subtreeUserIds(user);
    if (subtree.isEmpty()) return false;
    Set<Long> myProjects = new HashSet<>(accessService.accessibleProjectIds(user));
    Set<Long> officeInSubtree = accessService.officeIdsAmong(subtree);
    return underMe(t, subtree, myProjects, officeInSubtree);
  }

  /**
   * Task shows through the subtree branch when: assignee is in my role subtree, AND either
   * (a) that assignee is Office — projectId is a label, no intersection needed, OR
   * (b) the task has no project (project-less task, no intersection possible), OR
   * (c) the task's project is one I'm a member of.
   */
  private boolean underMe(
      TaskEntity t, Set<Long> subtree, Set<Long> myProjects, Set<Long> officeInSubtree) {
    Long assignee = t.getAssigneeId();
    if (assignee == null || !subtree.contains(assignee)) return false;
    if (officeInSubtree.contains(assignee)) return true;
    Long pid = t.getProjectId();
    if (pid == null) return true;
    return myProjects.contains(pid);
  }

  /** Is the user the assignee, a follower, or the creator of this task? */
  private boolean involves(Long userId, TaskEntity t) {
    return userId.equals(t.getAssigneeId())
        || (t.getFollowerIds() != null && t.getFollowerIds().contains(userId))
        || userId.equals(t.getCreatedBy());
  }

  // ---- Create / update ----

  @Transactional
  public TaskResponse create(AuthenticatedUser user, TaskUpsertRequest r) {
    TaskEntity t = new TaskEntity();
    t.setCode(nextCode());
    t.setCreatedBy(user.id());
    apply(t, r);
    // The creator automatically follows their own task (so it stays visible to them + they get updates).
    t.getFollowerIds().add(user.id());
    t.logActivity(user.id(), "Task created");
    routeCompletion(user, t, TaskStatus.PENDING); // if created as Completed, route through approval
    return mapper.toResponse(taskRepository.save(t));
  }

  @Transactional
  public TaskResponse update(AuthenticatedUser user, Long id, TaskUpsertRequest r) {
    TaskEntity t = requireTask(id);
    boolean wasCompleted = t.getStatus() == TaskStatus.COMPLETED;
    TaskStatus prevStatus = t.getStatus();
    apply(t, r);
    routeCompletion(user, t, prevStatus); // hold for approval when the actor needs sign-off
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
      TaskStatus target = TaskStatus.from(r.status());
      if (target != t.getStatus()) {
        if (target == TaskStatus.COMPLETED) {
          TaskStatus prev = t.getStatus();
          t.setStatus(TaskStatus.COMPLETED);
          routeCompletion(user, t, prev); // may hold for approval, or throw if blocked
          completedNow = t.getStatus() == TaskStatus.COMPLETED;
        } else {
          t.setStatus(target);
          clearApproval(t); // moving away from awaiting cancels the pending request
        }
        t.logActivity(user.id(), "Status changed to " + label(t.getStatus()));
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
        if (p == 100 && t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.AWAITING_APPROVAL) {
          TaskStatus prev = t.getStatus();
          t.setStatus(TaskStatus.COMPLETED);
          routeCompletion(user, t, prev);
          if (t.getStatus() == TaskStatus.COMPLETED) completedNow = true;
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
        TaskStatus target = TaskStatus.from(r.status());
        if (target != t.getStatus()) {
          if (target == TaskStatus.COMPLETED) {
            TaskStatus prev = t.getStatus();
            t.setStatus(TaskStatus.COMPLETED);
            routeCompletion(user, t, prev);
            if (t.getStatus() == TaskStatus.COMPLETED) completedNow.add(t);
          } else {
            t.setStatus(target);
            clearApproval(t);
          }
          t.logActivity(user.id(), "Status changed to " + label(t.getStatus()) + " (bulk)");
        }
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
      case AWAITING_APPROVAL -> "Awaiting Approval";
      case COMPLETED -> "Completed";
    };
  }

  // ---- Completion approval workflow ----

  /**
   * The role that must approve this user's completion of this task, or null when the user can
   * complete it directly (Super Admin, or top of their role ladder). Throws (422) when a report to a
   * Project Manager can't complete because the task has no project / no PM on it.
   */
  private Long resolveApproverRole(AuthenticatedUser user, TaskEntity task) {
    if (accessService.seesEverything(user)) return null; // Super Admin completes directly
    RoleEntity myRole = user.roleId() == null ? null : roleRepository.findById(user.roleId()).orElse(null);
    Long approverRoleId = myRole == null ? null : myRole.getReportsToRoleId();
    if (approverRoleId == null) return null; // no manager → direct
    RoleEntity approverRole = roleRepository.findById(approverRoleId).orElse(null);
    if (approverRole != null && PROJECT_MANAGER.equalsIgnoreCase(approverRole.getName())) {
      if (task.getProjectId() == null) {
        throw new IllegalStateException("Assign this task to a project first — its Project Manager approves completion.");
      }
      List<Long> pmIds = userRepository.findIdsByRoleIdIn(List.of(approverRoleId));
      boolean pmOnProject = pmIds.stream().anyMatch(pid -> memberRepository.existsByProjectIdAndUserId(task.getProjectId(), pid));
      if (!pmOnProject) {
        throw new IllegalStateException("No Project Manager is a member of this task's project yet — add one so they can approve completion.");
      }
    }
    return approverRoleId;
  }

  /** If a task's status has become COMPLETED but the actor needs sign-off, hold it for approval. */
  private void routeCompletion(AuthenticatedUser user, TaskEntity t, TaskStatus prevStatus) {
    if (t.getStatus() != TaskStatus.COMPLETED) return;
    Long approverRole = resolveApproverRole(user, t); // may throw (blocked)
    if (approverRole == null) {
      t.setProgress(100);
      clearApproval(t);
      return;
    }
    TaskStatus prev = (prevStatus == TaskStatus.COMPLETED || prevStatus == TaskStatus.AWAITING_APPROVAL)
        ? TaskStatus.IN_PROGRESS : prevStatus;
    t.setCompletionPrevStatus(prev.name());
    t.setCompletionRequestedBy(user.id());
    t.setCompletionApproverRoleId(approverRole);
    t.setCompletionNote(null);
    t.setStatus(TaskStatus.AWAITING_APPROVAL);
    t.logActivity(user.id(), "Requested completion — awaiting approval");
  }

  /** Clear the in-flight completion request fields (keeps completionNote as the last decision note). */
  private void clearApproval(TaskEntity t) {
    t.setCompletionRequestedBy(null);
    t.setCompletionApproverRoleId(null);
    t.setCompletionPrevStatus(null);
  }

  /** Can this user approve/reject the pending completion of this task? */
  private boolean canApprove(AuthenticatedUser user, TaskEntity t) {
    if (t.getStatus() != TaskStatus.AWAITING_APPROVAL || t.getCompletionApproverRoleId() == null) return false;
    if (!t.getCompletionApproverRoleId().equals(user.roleId())) return false; // must hold the approver role
    RoleEntity approverRole = roleRepository.findById(t.getCompletionApproverRoleId()).orElse(null);
    if (approverRole != null && PROJECT_MANAGER.equalsIgnoreCase(approverRole.getName())) {
      // Team Member → PM is project-scoped: only a PM on the task's project may approve.
      return t.getProjectId() != null && memberRepository.existsByProjectIdAndUserId(t.getProjectId(), user.id());
    }
    return true;
  }

  /** Tasks awaiting the signed-in user's approval — the approver's queue. */
  @Transactional(readOnly = true)
  public List<TaskResponse> pendingApprovals(AuthenticatedUser user) {
    return taskRepository.findAllByOrderByCreatedAtDesc().stream()
        .filter(t -> canApprove(user, t))
        .map(mapper::toResponse)
        .toList();
  }

  @Transactional
  public TaskResponse approveCompletion(AuthenticatedUser user, Long id) {
    TaskEntity t = requireTask(id);
    if (!canApprove(user, t)) throw new IllegalStateException("You aren't the approver for this task's completion.");
    boolean wasCompleted = t.getStatus() == TaskStatus.COMPLETED;
    t.setStatus(TaskStatus.COMPLETED);
    t.setProgress(100);
    clearApproval(t);
    t.setCompletionNote(null);
    t.logActivity(user.id(), "Approved completion");
    TaskEntity saved = taskRepository.save(t);
    if (!wasCompleted) spawnNextOccurrence(user, saved);
    return mapper.toResponse(saved);
  }

  @Transactional
  public TaskResponse rejectCompletion(AuthenticatedUser user, Long id, String note) {
    TaskEntity t = requireTask(id);
    if (!canApprove(user, t)) throw new IllegalStateException("You aren't the approver for this task's completion.");
    TaskStatus restore = t.getCompletionPrevStatus() != null
        ? TaskStatus.from(t.getCompletionPrevStatus()) : TaskStatus.IN_PROGRESS;
    String reason = note == null || note.isBlank() ? null : note.trim();
    t.setStatus(restore);
    clearApproval(t);
    t.setCompletionNote(reason);
    t.logActivity(user.id(), "Rejected completion" + (reason != null ? ": " + reason : ""));
    return mapper.toResponse(taskRepository.save(t));
  }

  private String label(TaskPriority p) {
    return switch (p) {
      case LOW -> "Low";
      case MEDIUM -> "Medium";
      case HIGH -> "High";
    };
  }
}
