package com.hitech.erp.payroll.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayPolicyRepository extends JpaRepository<HolidayPolicyEntity, Long> {
  List<HolidayPolicyEntity> findAllByOrderByYearDescNameAsc();
}
