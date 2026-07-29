package com.hitech.erp.payroll.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReimbursementRepository extends JpaRepository<ReimbursementEntity, Long> {
  List<ReimbursementEntity> findAllByOrderByAppliedAtDesc();

  List<ReimbursementEntity> findByUserIdOrderByAppliedAtDesc(Long userId);
}
