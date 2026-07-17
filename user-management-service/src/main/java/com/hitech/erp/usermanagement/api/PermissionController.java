package com.hitech.erp.usermanagement.api;

import com.hitech.erp.api.usermanagement.PermissionApi;
import com.hitech.erp.api.usermanagement.model.PermissionResponse;
import com.hitech.erp.usermanagement.service.ModuleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PermissionController implements PermissionApi {

  private final ModuleService moduleService;

  @Override
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:VIEW')")
  public ResponseEntity<List<PermissionResponse>> getPermissions() {
    return ResponseEntity.ok(moduleService.getAllPermissions());
  }
}
