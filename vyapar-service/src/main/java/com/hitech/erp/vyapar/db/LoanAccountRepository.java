package com.hitech.erp.vyapar.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanAccountRepository extends JpaRepository<LoanAccountEntity, Long> {
  List<LoanAccountEntity> findAllByOwnerUserIdOrderByIdDesc(Long ownerUserId);

  Optional<LoanAccountEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);
}
