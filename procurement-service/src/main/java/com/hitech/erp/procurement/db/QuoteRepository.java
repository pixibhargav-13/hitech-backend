package com.hitech.erp.procurement.db;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<QuoteEntity, Long> {
  /** One live quote per vendor per enquiry; a revision bumps the version in place. */
  Optional<QuoteEntity> findByRfq_IdAndVendorPartyId(Long rfqId, Long vendorPartyId);
}
