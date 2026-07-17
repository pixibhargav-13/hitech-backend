package com.hitech.erp.project.db;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProjectRepository
    extends JpaRepository<ProjectEntity, Long>, JpaSpecificationExecutor<ProjectEntity> {

  Page<ProjectEntity> findAll(Pageable pageable);
}
