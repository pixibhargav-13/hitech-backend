package com.hitech.erp.payroll.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<LoanEntity, Long> {
  List<LoanEntity> findAllByOrderByCreatedAtDesc();

  List<LoanEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
