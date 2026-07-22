package com.hitech.erp.vyapar.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccountEntity, Long> {
  List<BankAccountEntity> findAllByOwnerUserIdOrderByNameAsc(Long ownerUserId);

  Optional<BankAccountEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);
}
