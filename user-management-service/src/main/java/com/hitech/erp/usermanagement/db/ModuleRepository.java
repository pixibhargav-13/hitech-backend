package com.hitech.erp.usermanagement.db;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleRepository extends JpaRepository<ModuleEntity, Long> {

  Optional<ModuleEntity> findByCode(String code);
}
