package com.hitech.erp.payroll.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepository extends JpaRepository<LocationEntity, Long> {
  List<LocationEntity> findAllByOrderByNameAsc();

  /** Project ids the member belongs to (reads project_members — same DB, native to avoid coupling). */
  @Query(value = "SELECT pm.project_id FROM project_members pm WHERE pm.user_id = :userId", nativeQuery = true)
  List<Long> findProjectIdsForMember(@Param("userId") Long userId);

  /** A project's display name, for showing "which project" a linked site belongs to. */
  @Query(value = "SELECT p.name FROM projects p WHERE p.id = :projectId", nativeQuery = true)
  Optional<String> findProjectName(@Param("projectId") Long projectId);
}
