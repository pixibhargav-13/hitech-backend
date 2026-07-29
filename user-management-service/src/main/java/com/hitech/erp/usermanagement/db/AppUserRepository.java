package com.hitech.erp.usermanagement.db;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

  Optional<AppUserEntity> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByRoleId(Long roleId);

  boolean existsByDepartmentId(Long departmentId);

  long countByDepartmentId(Long departmentId);

  List<AppUserEntity> findAllByDepartmentId(Long departmentId);

  Page<AppUserEntity> findAll(Pageable pageable);

  /** User ids whose role is in the given set — used to expand a role subtree to its members. */
  @Query("SELECT u.id FROM AppUserEntity u WHERE u.role.id IN :roleIds")
  List<Long> findIdsByRoleIdIn(@Param("roleIds") Collection<Long> roleIds);

  /** Filter a set of user ids down to just the OFFICE-typed members. */
  @Query("SELECT u.id FROM AppUserEntity u WHERE u.id IN :userIds AND u.staffType = 'OFFICE'")
  List<Long> findOfficeIdsAmong(@Param("userIds") Collection<Long> userIds);
}
