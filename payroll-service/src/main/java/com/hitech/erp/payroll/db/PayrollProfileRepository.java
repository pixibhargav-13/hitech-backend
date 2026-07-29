package com.hitech.erp.payroll.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollProfileRepository extends JpaRepository<PayrollProfileEntity, Long> {
  Optional<PayrollProfileEntity> findByUserId(Long userId);

  List<PayrollProfileEntity> findAllByUserIdIn(List<Long> userIds);

  boolean existsByShiftId(Long shiftId);

  boolean existsByHolidayPolicyId(Long holidayPolicyId);

  boolean existsByLeavePolicyId(Long leavePolicyId);
}
