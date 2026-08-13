package com.hitech.erp.vyapar.db;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, Long> {
  List<InvoiceEntity> findAllByDocTypeOrderByIdDesc(String docType);

  List<InvoiceEntity> findAllByOrderByIdDesc();

  long countByDocType(String docType);

  /**
   * One party's documents. The party ledger used to load every invoice in the database and filter
   * in Java — O(all documents) on each click. This is O(that party's documents) and index-backed.
   */
  List<InvoiceEntity> findByParty_IdOrderByIdDesc(Long partyId);

  /** Open (unsettled, not cancelled) documents for the Link Payment dialog. */
  List<InvoiceEntity> findByParty_IdAndDocTypeInAndCancelledFalseOrderByIdDesc(
      Long partyId, Collection<String> docTypes);

  /**
   * Every stock movement for one item, as a flat projection.
   *
   * <p>The item ledger used to walk all invoices and touch {@code inv.getLines()} and
   * {@code inv.getParty()} on each — two lazy loads per invoice, so ~2N extra queries. This is a
   * single join that returns only the columns the ledger renders.
   *
   * <p>Columns: invoiceId, docType, invoiceNo, partyName, invoiceDate, quantity, rate, total,
   * paidAmount, cancelled.
   */
  @Query("""
      select i.id, i.docType, i.invoiceNo, p.name, i.invoiceDate,
             l.quantity, l.rate, i.total, i.paidAmount, i.cancelled
      from InvoiceLineEntity l
      join l.invoice i
      left join i.party p
      where l.itemId = :itemId
      order by i.id desc
      """)
  List<Object[]> findItemLedgerRows(@Param("itemId") Long itemId);
}
