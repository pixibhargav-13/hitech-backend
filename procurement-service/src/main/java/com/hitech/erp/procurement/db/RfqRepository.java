package com.hitech.erp.procurement.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RfqRepository extends JpaRepository<RfqEntity, Long> {
  List<RfqEntity> findAllByOrderByIdDesc();

  List<RfqEntity> findByProjectIdOrderByIdDesc(Long projectId);

  Optional<RfqEntity> findByRfqNo(String rfqNo);

  /** Backs the running number: RFQ-2026-001, -002 … per year. */
  long countByRfqNoStartingWith(String prefix);
}
