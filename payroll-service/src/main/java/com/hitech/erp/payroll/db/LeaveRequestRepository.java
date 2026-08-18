package com.hitech.erp.payroll.db;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequestEntity, Long> {

  List<LeaveRequestEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

  List<LeaveRequestEntity> findByStatusOrderByCreatedAtDesc(String status);

  /** Pending and decided together — the unified approvals screen. */
  List<LeaveRequestEntity> findAllByOrderByCreatedAtDesc();

  List<LeaveRequestEntity> findByUserIdAndStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
      Long userId, String status, LocalDate a, LocalDate b);
}
