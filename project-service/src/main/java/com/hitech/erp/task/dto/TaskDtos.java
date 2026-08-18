package com.hitech.erp.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response payloads for the task API. Grouped to keep the module compact. */
public final class TaskDtos {

  private TaskDtos() {}

  public record SubtaskDto(Long id, String title, boolean done, Long assigneeId, int sortOrder) {}

  public record CommentDto(Long id, Long authorId, String text, LocalDateTime at) {}

  public record AttachmentDto(
      Long id, Long uploadedBy, String name, String sizeLabel, String contentType, String dataUrl, LocalDateTime at) {}

  public record ActivityDto(Long id, Long actorId, String text, LocalDateTime at) {}

  public record TaskResponse(
      Long id,
      String code,
      String title,
      String description,
      Long projectId,
      Long assigneeId,
      Long createdBy,
      String clientName,
      String status,
      String priority,
      int progress,
      String dueDate,
      boolean draft,
      boolean pinned,
      String reminderAt,
      String recurrenceRule,
      int recurrenceInterval,
      String recurrenceUntil,
      Long seriesId,
      Long departmentId,
      List<Long> followerIds,
      List<SubtaskDto> subtasks,
      List<CommentDto> comments,
      List<AttachmentDto> attachments,
      List<ActivityDto> activity,
      Long completionRequestedBy,
      String completionNote,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {}

  /** Manager's reason when rejecting a completion request. */
  public record CompletionRejectRequest(String note) {}

  public record SubtaskInput(Long id, String title, boolean done, Long assigneeId) {}

  /** Full create/update of a task. */
  public record TaskUpsertRequest(
      @NotBlank String title,
      String description,
      Long projectId,
      @NotNull Long assigneeId,
      String clientName,
      String status,
      String priority,
      Integer progress,
      String dueDate,
      Boolean draft,
      Boolean pinned,
      String reminderAt,
      String recurrenceRule,
      Integer recurrenceInterval,
      String recurrenceUntil,
      Long departmentId,
      List<Long> followerIds,
      List<SubtaskInput> subtasks) {}

  /** Lightweight inline update from the list/main view — any field optional. */
  public record TaskPatchRequest(
      String status, String priority, Integer progress, Boolean pinned, String reminderAt) {}

  /** Apply one change to many tasks at once (bulk actions on the list). */
  public record BulkPatchRequest(
      @NotNull List<Long> taskIds, String status, String priority, Boolean pinned, Long assigneeId) {}

  /** Delete many tasks at once. */
  public record BulkDeleteRequest(@NotNull List<Long> taskIds) {}

  public record CommentInput(@NotBlank String text) {}

  public record AttachmentInput(
      @NotBlank String name, String sizeLabel, String contentType, String dataUrl) {}

  public record MembersUpdateRequest(@NotNull List<Long> userIds) {}

  /**
   * One project's task load, for the Project workspace Dashboard. Replaces the hand-typed
   * {@code projects.todo_count} column, which only ever showed whatever someone last saved.
   */
  public record ProjectWorkload(
      long total,
      long open,
      long inProgress,
      long completed,
      long overdue,
      long dueThisWeek,
      long awaitingApproval,
      /** Completed ÷ total as a percentage — the derived counterpart to the manual progress field. */
      int completionPercent) {}
}
