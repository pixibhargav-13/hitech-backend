package com.hitech.erp.procurement.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrderEntity, Long> {

  List<WorkOrderEntity> findAllByOrderByIdDesc();

  List<WorkOrderEntity> findByProjectIdOrderByIdDesc(Long projectId);

  /** Feeds the running number, so WO-2026-007 follows WO-2026-006 without a sequence table. */
  long countByWoNoStartingWith(String prefix);
}
