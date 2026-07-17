package com.hitech.erp.project.api;

import com.hitech.erp.api.project.LocationApi;
import com.hitech.erp.api.project.model.ProjectLocationCreateRequest;
import com.hitech.erp.api.project.model.ProjectLocationResponse;
import com.hitech.erp.api.project.model.ProjectLocationUpdateRequest;
import com.hitech.erp.project.service.ProjectLocationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LocationController implements LocationApi {

  private final ProjectLocationService service;

  @Override
  @PreAuthorize("hasAuthority('PROJECT:VIEW')")
  public ResponseEntity<List<ProjectLocationResponse>> getProjectLocations(Long projectId) {
    return ResponseEntity.ok(service.getLocations(projectId));
  }

  @Override
  @PreAuthorize("hasAuthority('PROJECT:CREATE')")
  public ResponseEntity<ProjectLocationResponse> createProjectLocation(
      Long projectId, ProjectLocationCreateRequest projectLocationCreateRequest) {
    return ResponseEntity.ok(service.create(projectId, projectLocationCreateRequest));
  }

  @Override
  @PreAuthorize("hasAuthority('PROJECT:EDIT')")
  public ResponseEntity<ProjectLocationResponse> updateProjectLocation(
      Long projectId, Long locationId, ProjectLocationUpdateRequest projectLocationUpdateRequest) {
    return ResponseEntity.ok(service.rename(projectId, locationId, projectLocationUpdateRequest));
  }

  @Override
  @PreAuthorize("hasAuthority('PROJECT:DELETE')")
  public ResponseEntity<Void> deleteProjectLocation(Long projectId, Long locationId) {
    service.delete(projectId, locationId);
    return ResponseEntity.noContent().build();
  }
}
