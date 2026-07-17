package com.hitech.erp.project.service;

import com.hitech.erp.api.project.model.ProjectLocationCreateRequest;
import com.hitech.erp.api.project.model.ProjectLocationResponse;
import com.hitech.erp.api.project.model.ProjectLocationUpdateRequest;
import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.project.db.ProjectLocationEntity;
import com.hitech.erp.project.db.ProjectLocationRepository;
import com.hitech.erp.project.db.ProjectRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectLocationService {

  private final ProjectLocationRepository repository;
  private final ProjectRepository projectRepository;

  @Transactional(readOnly = true)
  public List<ProjectLocationResponse> getLocations(Long projectId) {
    requireProject(projectId);
    return repository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public ProjectLocationResponse create(Long projectId, ProjectLocationCreateRequest request) {
    requireProject(projectId);

    Long parentId = request.getParentId();
    if (parentId != null) {
      parentId = requireLocation(projectId, parentId).getId();
    }

    ProjectLocationEntity entity = new ProjectLocationEntity();
    entity.setProjectId(projectId);
    entity.setParentId(parentId);
    entity.setName(request.getName().trim());

    final Long pid = parentId;
    long siblings =
        repository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
            .filter(l -> Objects.equals(l.getParentId(), pid))
            .count();
    entity.setSortOrder((int) siblings);

    return toResponse(repository.save(entity));
  }

  @Transactional
  public ProjectLocationResponse rename(
      Long projectId, Long locationId, ProjectLocationUpdateRequest request) {
    ProjectLocationEntity entity = requireLocation(projectId, locationId);
    entity.setName(request.getName().trim());
    return toResponse(repository.save(entity));
  }

  @Transactional
  public void delete(Long projectId, Long locationId) {
    ProjectLocationEntity entity = requireLocation(projectId, locationId);
    // Sub-locations cascade via the parent_id FK (ON DELETE CASCADE).
    repository.delete(entity);
  }

  private void requireProject(Long projectId) {
    if (!projectRepository.existsById(projectId)) {
      throw new EntityNotFoundException("Project not found: " + projectId);
    }
  }

  private ProjectLocationEntity requireLocation(Long projectId, Long locationId) {
    ProjectLocationEntity entity =
        repository
            .findById(locationId)
            .orElseThrow(() -> new EntityNotFoundException("Location not found: " + locationId));
    if (!Objects.equals(entity.getProjectId(), projectId)) {
      throw new EntityNotFoundException("Location not found in project: " + locationId);
    }
    return entity;
  }

  private ProjectLocationResponse toResponse(ProjectLocationEntity e) {
    return new ProjectLocationResponse()
        .id(e.getId())
        .projectId(e.getProjectId())
        .parentId(e.getParentId())
        .name(e.getName())
        .sortOrder(e.getSortOrder());
  }
}
