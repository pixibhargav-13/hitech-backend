package com.hitech.erp.usermanagement.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<DepartmentEntity, Long> {

  List<DepartmentEntity> findAllByOrderByNameAsc();

  Optional<DepartmentEntity> findByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCase(String name);

  long countByIdAndActiveTrue(Long id);
}
