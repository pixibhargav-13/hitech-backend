package com.hitech.erp.vyapar.db;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FirmProfileRepository extends JpaRepository<FirmProfileEntity, Long> {
  Optional<FirmProfileEntity> findByOwnerUserId(Long ownerUserId);
}
