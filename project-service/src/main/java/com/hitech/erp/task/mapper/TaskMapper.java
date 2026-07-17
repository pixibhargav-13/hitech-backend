package com.hitech.erp.task.mapper;

import com.hitech.erp.task.db.SubtaskEntity;
import com.hitech.erp.task.db.TaskActivityEntity;
import com.hitech.erp.task.db.TaskAttachmentEntity;
import com.hitech.erp.task.db.TaskCommentEntity;
import com.hitech.erp.task.db.TaskEntity;
import com.hitech.erp.task.dto.TaskDtos.ActivityDto;
import com.hitech.erp.task.dto.TaskDtos.AttachmentDto;
import com.hitech.erp.task.dto.TaskDtos.CommentDto;
import com.hitech.erp.task.dto.TaskDtos.SubtaskDto;
import com.hitech.erp.task.dto.TaskDtos.TaskResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Entity → response mapping. Manual (not MapStruct) to keep the nested-record shape explicit. */
@Component
public class TaskMapper {

  public TaskResponse toResponse(TaskEntity t) {
    return new TaskResponse(
        t.getId(),
        t.getCode(),
        t.getTitle(),
        t.getDescription(),
        t.getProjectId(),
        t.getAssigneeId(),
        t.getCreatedBy(),
        t.getClientName(),
        t.getStatus().name(),
        t.getPriority().name(),
        t.getProgress(),
        t.getDueDate(),
        t.isDraft(),
        new ArrayList<>(t.getFollowerIds()),
        t.getSubtasks().stream().map(this::toSubtask).toList(),
        t.getComments().stream().map(this::toComment).toList(),
        t.getAttachments().stream().map(this::toAttachment).toList(),
        t.getActivity().stream().map(this::toActivity).toList(),
        t.getCreatedAt(),
        t.getUpdatedAt());
  }

  public List<TaskResponse> toResponses(List<TaskEntity> tasks) {
    return tasks.stream().map(this::toResponse).toList();
  }

  private SubtaskDto toSubtask(SubtaskEntity s) {
    return new SubtaskDto(s.getId(), s.getTitle(), s.isDone(), s.getAssigneeId(), s.getSortOrder());
  }

  private CommentDto toComment(TaskCommentEntity c) {
    return new CommentDto(c.getId(), c.getAuthorId(), c.getText(), c.getCreatedAt());
  }

  private AttachmentDto toAttachment(TaskAttachmentEntity a) {
    return new AttachmentDto(
        a.getId(), a.getUploadedBy(), a.getName(), a.getSizeLabel(), a.getContentType(), a.getDataUrl(), a.getCreatedAt());
  }

  private ActivityDto toActivity(TaskActivityEntity a) {
    return new ActivityDto(a.getId(), a.getActorId(), a.getText(), a.getCreatedAt());
  }
}
