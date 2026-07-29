package com.hitech.erp.usermanagement.service;

import com.hitech.erp.api.usermanagement.model.RoleRequest;
import com.hitech.erp.api.usermanagement.model.RoleResponse;
import com.hitech.erp.common.exception.DuplicateValueException;
import com.hitech.erp.common.exception.EntityDeletionNotAllowedException;
import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import com.hitech.erp.usermanagement.db.PermissionEntity;
import com.hitech.erp.usermanagement.db.PermissionRepository;
import com.hitech.erp.usermanagement.db.RoleEntity;
import com.hitech.erp.usermanagement.db.RoleRepository;
import com.hitech.erp.usermanagement.mapper.UserManagementMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleService {

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final AppUserRepository userRepository;
  private final UserManagementMapper mapper;

  @Transactional(readOnly = true)
  public List<RoleResponse> getAllRoles() {
    return mapper.toRoleResponses(roleRepository.findAll());
  }

  @Transactional(readOnly = true)
  public RoleResponse getRoleById(Long id) {
    return mapper.toRoleResponse(requireRole(id));
  }

  @Transactional
  public RoleResponse createRole(RoleRequest request) {
    if (roleRepository.existsByNameIgnoreCase(request.getName())) {
      throw new DuplicateValueException("A role named '" + request.getName() + "' already exists");
    }

    RoleEntity role = new RoleEntity();
    role.setName(request.getName());
    role.setDescription(request.getDescription());
    role.setSystem(false);
    role.setReportsToRoleId(validateReportsTo(request.getReportsToRoleId(), null));
    role.setPermissions(resolvePermissions(request.getPermissionIds()));

    return mapper.toRoleResponse(roleRepository.save(role));
  }

  @Transactional
  public RoleResponse updateRole(Long id, RoleRequest request) {
    RoleEntity role = requireRole(id);

    if (!role.getName().equalsIgnoreCase(request.getName())
        && roleRepository.existsByNameIgnoreCase(request.getName())) {
      throw new DuplicateValueException("A role named '" + request.getName() + "' already exists");
    }

    role.setName(request.getName());
    role.setDescription(request.getDescription());
    role.setReportsToRoleId(validateReportsTo(request.getReportsToRoleId(), id));
    role.setPermissions(resolvePermissions(request.getPermissionIds()));

    return mapper.toRoleResponse(roleRepository.save(role));
  }

  @Transactional
  public void deleteRole(Long id) {
    RoleEntity role = requireRole(id);

    if (role.isSystem()) {
      throw new EntityDeletionNotAllowedException("System roles cannot be deleted");
    }
    if (userRepository.existsByRoleId(id)) {
      throw new EntityDeletionNotAllowedException(
          "Role is still assigned to one or more users and cannot be deleted");
    }

    roleRepository.delete(role);
  }

  private RoleEntity requireRole(Long id) {
    return roleRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));
  }

  /**
   * Validate a role's proposed parent in the reporting ladder: the parent must exist, a role can't
   * report to itself, and the chain must not form a loop. Returns the (validated) parent id, or null
   * for a top-of-ladder role. {@code selfId} is null when creating a brand-new role.
   */
  private Long validateReportsTo(Long parentId, Long selfId) {
    if (parentId == null) {
      return null;
    }
    if (selfId != null && parentId.equals(selfId)) {
      throw new IllegalArgumentException("A role cannot report to itself");
    }
    // Walk up the chain: every ancestor must exist, and we must never loop back to this role.
    Long cursor = parentId;
    int guard = 0;
    while (cursor != null) {
      if (selfId != null && cursor.equals(selfId)) {
        throw new IllegalArgumentException("That reporting line would create a loop");
      }
      final Long lookup = cursor;
      RoleEntity ancestor =
          roleRepository
              .findById(lookup)
              .orElseThrow(() -> new EntityNotFoundException("Parent role not found: " + lookup));
      cursor = ancestor.getReportsToRoleId();
      if (++guard > 1000) {
        // Defensive: an existing loop in data should never let us spin forever.
        throw new IllegalStateException("Reporting hierarchy is too deep or contains a loop");
      }
    }
    return parentId;
  }

  private Set<PermissionEntity> resolvePermissions(List<Long> permissionIds) {
    if (permissionIds == null || permissionIds.isEmpty()) {
      return new HashSet<>();
    }
    return new HashSet<>(permissionRepository.findAllByIdIn(permissionIds));
  }
}
