package com.hitech.erp.vyapar.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, Long> {
  List<InvoiceEntity> findAllByDocTypeOrderByIdDesc(String docType);

  List<InvoiceEntity> findAllByOrderByIdDesc();

  long countByDocType(String docType);
}
