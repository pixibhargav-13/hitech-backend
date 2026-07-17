package com.hitech.erp.task.api;

import com.hitech.erp.task.dto.TaskDtos.AttachmentInput;
import com.hitech.erp.task.dto.TaskDtos.CommentInput;
import com.hitech.erp.task.dto.TaskDtos.TaskPatchRequest;
import com.hitech.erp.task.dto.TaskDtos.TaskResponse;
import com.hitech.erp.task.dto.TaskDtos.TaskUpsertRequest;
import com.hitech.erp.task.security.CurrentUser;
import com.hitech.erp.task.service.TaskService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Taskopad task API. Access is gated coarsely by PROJECT authorities and then row-filtered per user
 * inside {@link TaskService} (Super Admin sees all; others see their projects + own tasks).
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

  private final TaskService taskService;

  @GetMapping
  @PreAuthorize("hasAuthority('TASKOPAD:VIEW')")
  public ResponseEntity<List<TaskResponse>> list(
      @RequestParam(name = "projectId", required = false) Long projectId) {
    return ResponseEntity.ok(taskService.list(CurrentUser.get(), projectId));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('TASKOPAD:VIEW')")
  public ResponseEntity<TaskResponse> get(@PathVariable("id") Long id) {
    return ResponseEntity.ok(taskService.get(CurrentUser.get(), id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('TASKOPAD:CREATE')")
  public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskUpsertRequest request) {
    return ResponseEntity.ok(taskService.create(CurrentUser.get(), request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('TASKOPAD:EDIT')")
  public ResponseEntity<TaskResponse> update(
      @PathVariable("id") Long id, @Valid @RequestBody TaskUpsertRequest request) {
    return ResponseEntity.ok(taskService.update(CurrentUser.get(), id, request));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('TASKOPAD:EDIT')")
  public ResponseEntity<TaskResponse> patch(
      @PathVariable("id") Long id, @RequestBody TaskPatchRequest request) {
    return ResponseEntity.ok(taskService.patch(CurrentUser.get(), id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('TASKOPAD:DELETE')")
  public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
    taskService.delete(CurrentUser.get(), id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/comments")
  @PreAuthorize("hasAuthority('TASKOPAD:VIEW')")
  public ResponseEntity<TaskResponse> addComment(
      @PathVariable("id") Long id, @Valid @RequestBody CommentInput request) {
    return ResponseEntity.ok(taskService.addComment(CurrentUser.get(), id, request));
  }

  @PostMapping("/{id}/attachments")
  @PreAuthorize("hasAuthority('TASKOPAD:EDIT')")
  public ResponseEntity<TaskResponse> addAttachment(
      @PathVariable("id") Long id, @Valid @RequestBody AttachmentInput request) {
    return ResponseEntity.ok(taskService.addAttachment(CurrentUser.get(), id, request));
  }

  @PostMapping("/{id}/subtasks/{subtaskId}/toggle")
  @PreAuthorize("hasAuthority('TASKOPAD:EDIT')")
  public ResponseEntity<TaskResponse> toggleSubtask(
      @PathVariable("id") Long id, @PathVariable("subtaskId") Long subtaskId) {
    return ResponseEntity.ok(taskService.toggleSubtask(CurrentUser.get(), id, subtaskId));
  }
}
