package com.hitech.erp.payroll.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipRepository extends JpaRepository<PayslipEntity, Long> {
  List<PayslipEntity> findByRunIdOrderByIdAsc(Long runId);

  Optional<PayslipEntity> findByRunIdAndUserId(Long runId, Long userId);

  List<PayslipEntity> findByUserIdOrderByIdDesc(Long userId);
}
