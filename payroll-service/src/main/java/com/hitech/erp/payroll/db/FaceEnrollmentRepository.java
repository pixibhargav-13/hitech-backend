package com.hitech.erp.payroll.db;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaceEnrollmentRepository extends JpaRepository<FaceEnrollmentEntity, Long> {
  Optional<FaceEnrollmentEntity> findByUserId(Long userId);
}
