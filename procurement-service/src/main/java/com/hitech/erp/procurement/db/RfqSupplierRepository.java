package com.hitech.erp.procurement.db;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RfqSupplierRepository extends JpaRepository<RfqSupplierEntity, Long> {
  /** The whole public surface: a token resolves to exactly one supplier on one enquiry. */
  Optional<RfqSupplierEntity> findByShareToken(String shareToken);
}
