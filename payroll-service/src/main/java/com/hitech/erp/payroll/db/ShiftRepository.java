package com.hitech.erp.payroll.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<ShiftEntity, Long> {
  List<ShiftEntity> findAllByOrderByNameAsc();
}
