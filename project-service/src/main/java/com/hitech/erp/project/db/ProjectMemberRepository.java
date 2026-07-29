package com.hitech.erp.project.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, Long> {

  List<ProjectMemberEntity> findByProjectId(Long projectId);

  @Query("SELECT m.projectId FROM ProjectMemberEntity m WHERE m.userId = :userId")
  List<Long> findProjectIdsByUserId(@Param("userId") Long userId);

  @Query("SELECT m.userId FROM ProjectMemberEntity m WHERE m.projectId = :projectId")
  List<Long> findUserIdsByProjectId(@Param("projectId") Long projectId);

  boolean existsByProjectIdAndUserId(Long projectId, Long userId);

  /**
   * Immediate bulk delete. A derived delete would SELECT-then-remove each entity, and Hibernate
   * flushes those DELETEs *after* the INSERTs that follow in {@code setMembers} — re-inserting an
   * existing (project_id, user_id) then trips the unique constraint. A modifying bulk delete runs
   * the SQL DELETE up front; flush/clear keep the persistence context consistent around it.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("DELETE FROM ProjectMemberEntity m WHERE m.projectId = :projectId")
  void deleteByProjectId(@Param("projectId") Long projectId);
}
