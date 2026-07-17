package com.hitech.erp.usermanagement.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {

  List<PermissionEntity> findAllByIdIn(List<Long> ids);
}
