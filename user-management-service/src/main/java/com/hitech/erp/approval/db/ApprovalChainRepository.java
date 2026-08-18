package com.hitech.erp.approval.db;

import com.hitech.erp.approval.db.ApprovalEntities.Chain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Chain configuration. Top-level rather than nested inside a holder class — Spring Data only scans
 * top-level interfaces, so a nested one silently produces no bean.
 */
public interface ApprovalChainRepository extends JpaRepository<Chain, Long> {

  Optional<Chain> findByEntityType(String entityType);
}
