package com.hitech.erp.vyapar.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
  List<PaymentEntity> findAllByOrderByIdDesc();

  List<PaymentEntity> findAllByDirectionOrderByIdDesc(String direction);

  /** One party's receipts/payments — see the note on {@code InvoiceRepository#findByParty_Id…}. */
  List<PaymentEntity> findByParty_IdOrderByIdDesc(Long partyId);
}
