package com.hitech.erp.project.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, Long> {

  List<ProjectMemberEntity> findByProjectId(Long projectId);

  @Query("SELECT m.projectId FROM ProjectMemberEntity m WHERE m.userId = :userId")
  List<Long> findProjectIdsByUserId(@Param("userId") Long userId);

  @Query("SELECT m.userId FROM ProjectMemberEntity m WHERE m.projectId = :projectId")
  List<Long> findUserIdsByProjectId(@Param("projectId") Long projectId);

  boolean existsByProjectIdAndUserId(Long projectId, Long userId);

  void deleteByProjectId(Long projectId);
}
