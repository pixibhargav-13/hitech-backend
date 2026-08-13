package com.hitech.erp.vyapar.db;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentLinkRepository extends JpaRepository<PaymentLinkEntity, Long> {

  List<PaymentLinkEntity> findByPaymentId(Long paymentId);

  List<PaymentLinkEntity> findByInvoiceId(Long invoiceId);

  /**
   * Bulk variants. A ledger renders many payments at once, so loading links one payment at a time
   * would be N+1; these fetch every link for the page in a single indexed query instead.
   */
  List<PaymentLinkEntity> findByPaymentIdIn(Collection<Long> paymentIds);

  List<PaymentLinkEntity> findByInvoiceIdIn(Collection<Long> invoiceIds);

  void deleteByPaymentId(Long paymentId);

  void deleteByInvoiceId(Long invoiceId);
}
