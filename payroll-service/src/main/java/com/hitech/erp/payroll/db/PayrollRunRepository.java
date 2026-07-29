package com.hitech.erp.payroll.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunRepository extends JpaRepository<PayrollRunEntity, Long> {
  Optional<PayrollRunEntity> findByMonth(String month);

  List<PayrollRunEntity> findAllByOrderByMonthDesc();
}
