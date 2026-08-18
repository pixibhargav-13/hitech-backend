package com.hitech.erp.tender.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TenderRepository
    extends JpaRepository<TenderEntity, Long>, JpaSpecificationExecutor<TenderEntity> {

  /** Tenders handed off to a project — see {@code TenderService#byProject}. */
  java.util.List<TenderEntity> findByProjectIdOrderByIdDesc(Long projectId);
}
