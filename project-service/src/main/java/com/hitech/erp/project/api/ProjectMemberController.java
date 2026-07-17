package com.hitech.erp.project.api;

import com.hitech.erp.project.service.ProjectMemberService;
import com.hitech.erp.task.dto.TaskDtos.MembersUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manage which users can access a project (drives per-user project/task visibility). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

  private final ProjectMemberService memberService;

  @GetMapping
  @PreAuthorize("hasAuthority('PROJECT:VIEW')")
  public ResponseEntity<List<Long>> getMembers(@PathVariable("projectId") Long projectId) {
    return ResponseEntity.ok(memberService.getMemberIds(projectId));
  }

  @PutMapping
  @PreAuthorize("hasAuthority('PROJECT:EDIT')")
  public ResponseEntity<List<Long>> setMembers(
      @PathVariable("projectId") Long projectId, @Valid @RequestBody MembersUpdateRequest request) {
    return ResponseEntity.ok(memberService.setMembers(projectId, request.userIds()));
  }
}
