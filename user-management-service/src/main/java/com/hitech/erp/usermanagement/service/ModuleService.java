package com.hitech.erp.usermanagement.service;

import com.hitech.erp.api.usermanagement.model.ModuleResponse;
import com.hitech.erp.api.usermanagement.model.PermissionResponse;
import com.hitech.erp.usermanagement.db.ModuleRepository;
import com.hitech.erp.usermanagement.db.PermissionRepository;
import com.hitech.erp.usermanagement.mapper.UserManagementMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModuleService {

  private final ModuleRepository moduleRepository;
  private final PermissionRepository permissionRepository;
  private final UserManagementMapper mapper;

  @Transactional(readOnly = true)
  public List<ModuleResponse> getAllModules() {
    return mapper.toModuleResponses(moduleRepository.findAll());
  }

  @Transactional(readOnly = true)
  public List<PermissionResponse> getAllPermissions() {
    return mapper.toPermissionResponses(permissionRepository.findAll());
  }
}
