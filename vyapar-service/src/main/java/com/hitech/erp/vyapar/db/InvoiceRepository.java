package com.hitech.erp.vyapar.db;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, Long> {
  List<InvoiceEntity> findAllByDocTypeOrderByIdDesc(String docType);

  List<InvoiceEntity> findAllByOrderByIdDesc();

  /**
   * Documents settled through one bank/cash account. A bill paid at the counter moves that
   * account's money just as a separate receipt does, so it belongs in the account's ledger.
   * Cancelled documents are excluded — they keep their number but stop counting.
   */
  @Query(
      """
      SELECT i FROM InvoiceEntity i
      WHERE i.bankAccountId = :accountId AND i.paidAmount > 0 AND i.cancelled = false
      ORDER BY i.id DESC
      """)
  List<InvoiceEntity> findSettledByBankAccount(@Param("accountId") Long accountId);

  /**
   * Net movement per account from documents. A sale brings money in; a purchase, an expense or a
   * credit note pays it out.
   */
  @Query(
      """
      SELECT i.bankAccountId,
             SUM(CASE WHEN i.docType IN ('SALE', 'PURCHASE_RETURN') THEN i.paidAmount ELSE -i.paidAmount END)
      FROM InvoiceEntity i
      WHERE i.bankAccountId IS NOT NULL AND i.paidAmount > 0 AND i.cancelled = false
      GROUP BY i.bankAccountId
      """)
  List<Object[]> sumPaidByBankAccount();

  /** The same, limited to one project. */
  @Query("""
      SELECT i.bankAccountId,
             SUM(CASE WHEN i.docType IN ('SALE', 'PURCHASE_RETURN') THEN i.paidAmount ELSE -i.paidAmount END)
      FROM InvoiceEntity i
      WHERE i.bankAccountId IS NOT NULL AND i.paidAmount > 0 AND i.cancelled = false
        AND i.projectId = :projectId
      GROUP BY i.bankAccountId
      """)
  List<Object[]> sumPaidByBankAccountForProject(@Param("projectId") Long projectId);

  @Query("""
      SELECT i FROM InvoiceEntity i
      WHERE i.bankAccountId = :accountId AND i.paidAmount > 0 AND i.cancelled = false
        AND i.projectId = :projectId
      ORDER BY i.id DESC
      """)
  List<InvoiceEntity> findSettledByBankAccountAndProject(@Param("accountId") Long accountId,
                                                         @Param("projectId") Long projectId);

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

  List<InvoiceEntity> findByProjectIdOrderByIdDesc(Long projectId);

  /**
   * Every material movement on one project, as a flat projection — the Project workspace's Material
   * tab. Items are global master data, so what's project-specific is the <em>movement</em>: the
   * lines of documents filed against this project. Cancelled documents move nothing.
   *
   * <p>Columns: invoiceId, docType, invoiceNo, partyName, invoiceDate, itemName, quantity, unit,
   * rate, amount.
   */
  @Query("""
      select i.id, i.docType, i.invoiceNo, p.name, i.invoiceDate,
             l.itemName, l.quantity, l.unit, l.rate, l.amount
      from InvoiceLineEntity l
      join l.invoice i
      left join i.party p
      where i.projectId = :projectId
        and i.cancelled = false
      order by i.id desc, l.sortOrder asc
      """)
  List<Object[]> findProjectMaterialRows(@Param("projectId") Long projectId);
}
