package com.hitech.erp.vyapar.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
  List<PaymentEntity> findAllByOrderByIdDesc();

  List<PaymentEntity> findAllByDirectionOrderByIdDesc(String direction);

  /** One party's receipts/payments — see the note on {@code InvoiceRepository#findByParty_Id…}. */
  List<PaymentEntity> findByParty_IdOrderByIdDesc(Long partyId);

  List<PaymentEntity> findByProjectIdOrderByIdDesc(Long projectId);

  /** Receipts that moved through one bank/cash account — half of that account's ledger. */
  List<PaymentEntity> findByBankAccountIdOrderByIdDesc(Long bankAccountId);

  /**
   * Net movement per account in one grouped query. IN is money arriving, OUT is money leaving,
   * so the sign follows the direction.
   */
  @Query(
      """
      SELECT p.bankAccountId, SUM(CASE WHEN p.direction = 'IN' THEN p.amount ELSE -p.amount END)
      FROM PaymentEntity p
      WHERE p.bankAccountId IS NOT NULL
      GROUP BY p.bankAccountId
      """)
  List<Object[]> sumByBankAccount();

  /** The same, limited to one project — what the header's project scope asks for. */
  @Query("""
      SELECT p.bankAccountId, SUM(CASE WHEN p.direction = 'IN' THEN p.amount ELSE -p.amount END)
      FROM PaymentEntity p
      WHERE p.bankAccountId IS NOT NULL AND p.projectId = :projectId
      GROUP BY p.bankAccountId
      """)
  List<Object[]> sumByBankAccountForProject(@Param("projectId") Long projectId);

  List<PaymentEntity> findByBankAccountIdAndProjectIdOrderByIdDesc(Long bankAccountId, Long projectId);
}
