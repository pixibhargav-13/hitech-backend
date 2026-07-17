package com.hitech.erp.project.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectLocationRepository extends JpaRepository<ProjectLocationEntity, Long> {

  List<ProjectLocationEntity> findByProjectIdOrderBySortOrderAscIdAsc(Long projectId);

  List<ProjectLocationEntity> findByParentId(Long parentId);

  long countByProjectIdAndParentId(Long projectId, Long parentId);
}
