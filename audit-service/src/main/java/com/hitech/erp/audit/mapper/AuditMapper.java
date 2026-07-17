package com.hitech.erp.audit.mapper;

import com.hitech.erp.api.audit.model.AuditLogResponse;
import com.hitech.erp.audit.db.AuditLogEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuditMapper {

  @Mapping(target = "action", expression = "java(log.getAction() == null ? null : log.getAction().name())")
  @Mapping(
      target = "createdAt",
      expression = "java(log.getCreatedAt() == null ? null : log.getCreatedAt().toString())")
  AuditLogResponse toResponse(AuditLogEntity log);

  List<AuditLogResponse> toResponses(List<AuditLogEntity> logs);
}
