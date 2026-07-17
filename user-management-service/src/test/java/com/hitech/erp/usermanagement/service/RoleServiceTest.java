package com.hitech.erp.usermanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hitech.erp.api.usermanagement.model.RoleRequest;
import com.hitech.erp.api.usermanagement.model.RoleResponse;
import com.hitech.erp.common.exception.DuplicateValueException;
import com.hitech.erp.common.exception.EntityDeletionNotAllowedException;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import com.hitech.erp.usermanagement.db.ModuleEntity;
import com.hitech.erp.usermanagement.db.PermissionAction;
import com.hitech.erp.usermanagement.db.PermissionEntity;
import com.hitech.erp.usermanagement.db.PermissionRepository;
import com.hitech.erp.usermanagement.db.RoleEntity;
import com.hitech.erp.usermanagement.db.RoleRepository;
import com.hitech.erp.usermanagement.mapper.UserManagementMapper;
import com.hitech.erp.usermanagement.mapper.UserManagementMapperImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

  @Mock private RoleRepository roleRepository;
  @Mock private PermissionRepository permissionRepository;
  @Mock private AppUserRepository userRepository;

  private final UserManagementMapper mapper = new UserManagementMapperImpl();

  private RoleService roleService;

  @BeforeEach
  void setUp() {
    roleService = new RoleService(roleRepository, permissionRepository, userRepository, mapper);
  }

  private PermissionEntity permission(String moduleCode, PermissionAction action) {
    ModuleEntity module = new ModuleEntity();
    module.setCode(moduleCode);
    module.setName(moduleCode);

    PermissionEntity permission = new PermissionEntity();
    permission.setModule(module);
    permission.setAction(action);
    return permission;
  }

  @Test
  void createRoleSucceedsWithPermissions() {
    RoleRequest request = new RoleRequest().name("Project Manager").description("Manages projects");
    request.setPermissionIds(List.of(1L, 2L));

    when(roleRepository.existsByNameIgnoreCase("Project Manager")).thenReturn(false);
    when(permissionRepository.findAllByIdIn(List.of(1L, 2L)))
        .thenReturn(List.of(permission("PROJECT", PermissionAction.VIEW), permission("PROJECT", PermissionAction.EDIT)));
    when(roleRepository.save(any(RoleEntity.class)))
        .thenAnswer(
            invocation -> {
              RoleEntity entity = invocation.getArgument(0);
              entity.setId(100L);
              return entity;
            });

    RoleResponse response = roleService.createRole(request);

    assertThat(response.getName()).isEqualTo("Project Manager");
    assertThat(response.getId()).isEqualTo(100L);
  }

  @Test
  void createRoleRejectsDuplicateName() {
    RoleRequest request = new RoleRequest().name("Super Admin");
    when(roleRepository.existsByNameIgnoreCase("Super Admin")).thenReturn(true);

    assertThatThrownBy(() -> roleService.createRole(request))
        .isInstanceOf(DuplicateValueException.class);
  }

  @Test
  void deleteRoleBlockedForSystemRole() {
    RoleEntity systemRole = new RoleEntity();
    systemRole.setId(1L);
    systemRole.setSystem(true);
    when(roleRepository.findById(1L)).thenReturn(Optional.of(systemRole));

    assertThatThrownBy(() -> roleService.deleteRole(1L))
        .isInstanceOf(EntityDeletionNotAllowedException.class);
  }

  @Test
  void deleteRoleBlockedWhenAssignedToUsers() {
    RoleEntity role = new RoleEntity();
    role.setId(2L);
    role.setSystem(false);
    when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
    when(userRepository.existsByRoleId(2L)).thenReturn(true);

    assertThatThrownBy(() -> roleService.deleteRole(2L))
        .isInstanceOf(EntityDeletionNotAllowedException.class);
  }
}
