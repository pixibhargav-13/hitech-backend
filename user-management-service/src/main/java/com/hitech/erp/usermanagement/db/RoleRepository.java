package com.hitech.erp.usermanagement.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

  Optional<RoleEntity> findByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCase(String name);

  /** Direct children in the reports-to ladder — one step down. */
  @Query("SELECT r.id FROM RoleEntity r WHERE r.reportsToRoleId = :parentId")
  List<Long> findChildRoleIds(@Param("parentId") Long parentId);
}
