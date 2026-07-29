package com.hitech.erp.payroll.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicyEntity, Long> {
  List<LeavePolicyEntity> findAllByOrderByNameAsc();
}
