package com.hitech.erp.payroll.db;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<AttendanceEntity, Long> {

  Optional<AttendanceEntity> findByUserIdAndDate(Long userId, LocalDate date);

  List<AttendanceEntity> findByUserIdAndDateBetweenOrderByDateAsc(Long userId, LocalDate from, LocalDate to);

  List<AttendanceEntity> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

  List<AttendanceEntity> findByProjectIdAndDateBetweenOrderByDateAsc(Long projectId, LocalDate from, LocalDate to);

  List<AttendanceEntity> findByUserIdInAndDateBetween(List<Long> userIds, LocalDate from, LocalDate to);

  /** Bulk-clear attendance rows in a date range (admin housekeeping / test reset). */
  @Modifying
  @Query("DELETE FROM AttendanceEntity a WHERE a.date BETWEEN :from AND :to")
  int deleteByDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
