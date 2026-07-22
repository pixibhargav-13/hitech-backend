package com.hitech.erp.usermanagement.db;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

  Optional<AppUserEntity> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByRoleId(Long roleId);

  boolean existsByDepartmentId(Long departmentId);

  long countByDepartmentId(Long departmentId);

  java.util.List<AppUserEntity> findAllByDepartmentId(Long departmentId);

  Page<AppUserEntity> findAll(Pageable pageable);
}
