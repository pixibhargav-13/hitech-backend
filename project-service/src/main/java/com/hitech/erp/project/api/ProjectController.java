package com.hitech.erp.project.api;

import com.hitech.erp.api.project.ProjectApi;
import com.hitech.erp.api.project.model.ProjectCreateRequest;
import com.hitech.erp.api.project.model.ProjectPageResponse;
import com.hitech.erp.api.project.model.ProjectResponse;
import com.hitech.erp.api.project.model.ProjectUpdateRequest;
import com.hitech.erp.project.service.ProjectService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProjectController implements ProjectApi {

  private final ProjectService projectService;

  @Override
  @PreAuthorize("hasAuthority('PROJECT:VIEW')")
  public ResponseEntity<ProjectPageResponse> getProjects(
      Optional<Integer> page, Optional<Integer> size, Optional<String> status, Optional<String> q) {
    return ResponseEntity.ok(
        projectService.getProjects(page.orElse(0), size.orElse(20), status.orElse(null), q.orElse(null)));
  }

  @Override
  @PreAuthorize("hasAuthority('PROJECT:VIEW')")
  public ResponseEntity<ProjectResponse> getProjectById(Long id) {
    return ResponseEntity.ok(projectService.getProjectById(id));
  }

  @Override
  @PreAuthorize("hasAuthority('PROJECT:CREATE')")
  public ResponseEntity<ProjectResponse> createProject(ProjectCreateRequest projectCreateRequest) {
    return ResponseEntity.ok(projectService.createProject(projectCreateRequest));
  }

  @Override
  @PreAuthorize("hasAuthority('PROJECT:EDIT')")
  public ResponseEntity<ProjectResponse> updateProject(Long id, ProjectUpdateRequest projectUpdateRequest) {
    return ResponseEntity.ok(projectService.updateProject(id, projectUpdateRequest));
  }

  @Override
  @PreAuthorize("hasAuthority('PROJECT:DELETE')")
  public ResponseEntity<Void> deleteProject(Long id) {
    projectService.deleteProject(id);
    return ResponseEntity.noContent().build();
  }
}
