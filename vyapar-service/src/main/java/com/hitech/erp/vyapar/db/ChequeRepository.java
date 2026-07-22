package com.hitech.erp.vyapar.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChequeRepository extends JpaRepository<ChequeEntity, Long> {
  List<ChequeEntity> findAllByOwnerUserIdOrderByIdDesc(Long ownerUserId);

  Optional<ChequeEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);
}
