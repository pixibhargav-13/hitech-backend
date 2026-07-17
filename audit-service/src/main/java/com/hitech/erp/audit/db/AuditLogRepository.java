package com.hitech.erp.audit.db;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository
    extends JpaRepository<AuditLogEntity, Long>, JpaSpecificationExecutor<AuditLogEntity> {

  Page<AuditLogEntity> findAll(Pageable pageable);

  @Query("SELECT DISTINCT a.entityType FROM AuditLogEntity a WHERE a.entityType IS NOT NULL ORDER BY a.entityType")
  List<String> findDistinctEntityTypes();

  @Query(
      "SELECT DISTINCT a.actorUserId, a.actorName, a.actorEmail FROM AuditLogEntity a "
          + "WHERE a.actorUserId IS NOT NULL ORDER BY a.actorName")
  List<Object[]> findDistinctActors();
}
