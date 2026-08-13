package com.hitech.erp.vyapar.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceHistoryRepository extends JpaRepository<InvoiceHistoryEntity, Long> {

  /** Newest first; index-backed on (invoice_id, id desc). */
  List<InvoiceHistoryEntity> findByInvoiceIdOrderByIdDesc(Long invoiceId);
}
