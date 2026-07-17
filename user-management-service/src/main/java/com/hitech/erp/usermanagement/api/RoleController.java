package com.hitech.erp.usermanagement.api;

import com.hitech.erp.api.usermanagement.RoleApi;
import com.hitech.erp.api.usermanagement.model.RoleRequest;
import com.hitech.erp.api.usermanagement.model.RoleResponse;
import com.hitech.erp.usermanagement.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RoleController implements RoleApi {

  private final RoleService roleService;

  @Override
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:VIEW')")
  public ResponseEntity<java.util.List<RoleResponse>> getRoles() {
    return ResponseEntity.ok(roleService.getAllRoles());
  }

  @Override
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:VIEW')")
  public ResponseEntity<RoleResponse> getRoleById(Long id) {
    return ResponseEntity.ok(roleService.getRoleById(id));
  }

  @Override
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:CREATE')")
  public ResponseEntity<RoleResponse> createRole(RoleRequest roleRequest) {
    return ResponseEntity.ok(roleService.createRole(roleRequest));
  }

  @Override
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:EDIT')")
  public ResponseEntity<RoleResponse> updateRole(Long id, RoleRequest roleRequest) {
    return ResponseEntity.ok(roleService.updateRole(id, roleRequest));
  }

  @Override
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:DELETE')")
  public ResponseEntity<Void> deleteRole(Long id) {
    roleService.deleteRole(id);
    return ResponseEntity.noContent().build();
  }
}
