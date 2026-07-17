package com.hitech.erp.usermanagement.mapper;

import com.hitech.erp.api.usermanagement.model.ModuleResponse;
import com.hitech.erp.api.usermanagement.model.PermissionResponse;
import com.hitech.erp.api.usermanagement.model.RoleResponse;
import com.hitech.erp.api.usermanagement.model.RoleSummary;
import com.hitech.erp.api.usermanagement.model.UserResponse;
import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.ModuleEntity;
import com.hitech.erp.usermanagement.db.PermissionEntity;
import com.hitech.erp.usermanagement.db.RoleEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserManagementMapper {

  @Mapping(target = "moduleCode", source = "module.code")
  @Mapping(target = "moduleName", source = "module.name")
  @Mapping(target = "action", expression = "java(permission.getAction().name())")
  @Mapping(target = "code", expression = "java(permission.getCode())")
  PermissionResponse toPermissionResponse(PermissionEntity permission);

  List<PermissionResponse> toPermissionResponses(List<PermissionEntity> permissions);

  ModuleResponse toModuleResponse(ModuleEntity module);

  List<ModuleResponse> toModuleResponses(List<ModuleEntity> modules);

  RoleSummary toRoleSummary(RoleEntity role);

  // isSystem uses expression mapping: Lombok's isSystem() getter introspects as JavaBean property
  // "system" (the leading "is" is treated as the boolean prefix), not "isSystem", so implicit
  // name-based mapping silently leaves the target field null.
  @Mapping(target = "isSystem", expression = "java(role.isSystem())")
  RoleResponse toRoleResponse(RoleEntity role);

  List<RoleResponse> toRoleResponses(List<RoleEntity> roles);

  // Same Lombok boolean-getter quirk as RoleEntity.isSystem: isActive() introspects as property
  // "active", not "isActive", so it needs an explicit expression mapping too.
  @Mapping(target = "isActive", expression = "java(user.isActive())")
  UserResponse toUserResponse(AppUserEntity user);

  List<UserResponse> toUserResponses(List<AppUserEntity> users);
}
