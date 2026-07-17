package com.hitech.erp.project.mapper;

import com.hitech.erp.api.project.model.ProjectResponse;
import com.hitech.erp.project.db.ProjectEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

  @Mapping(target = "status", expression = "java(project.getStatus() == null ? null : project.getStatus().name())")
  @Mapping(target = "health", expression = "java(project.getHealth() == null ? null : project.getHealth().name())")
  ProjectResponse toResponse(ProjectEntity project);

  List<ProjectResponse> toResponses(List<ProjectEntity> projects);
}
