package com.hitech.erp.task.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

  /** Newest first. Super Admin path — no access restriction. */
  List<TaskEntity> findAllByOrderByCreatedAtDesc();

  /**
   * Access-scoped listing: a task is visible when it belongs to one of the user's accessible
   * projects, or the user is its assignee / a follower / its creator.
   */
  @Query(
      "SELECT DISTINCT t FROM TaskEntity t LEFT JOIN t.followerIds f "
          + "WHERE (t.projectId IN :projectIds) "
          + "   OR t.assigneeId = :userId "
          + "   OR t.createdBy = :userId "
          + "   OR f = :userId "
          + "ORDER BY t.createdAt DESC")
  List<TaskEntity> findAccessible(
      @Param("projectIds") List<Long> projectIds, @Param("userId") Long userId);

  long countByProjectId(Long projectId);
}
