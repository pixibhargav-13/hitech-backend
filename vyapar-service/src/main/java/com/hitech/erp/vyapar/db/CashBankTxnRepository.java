package com.hitech.erp.vyapar.db;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashBankTxnRepository extends JpaRepository<CashBankTxnEntity, Long> {

  /** Bank ledger — one account, newest first. */
  List<CashBankTxnEntity> findAllByOwnerUserIdAndAccountIdOrderByIdDesc(Long ownerUserId, Long accountId);

  /** Cash-in-hand ledger — the rows with no bank account behind them. */
  List<CashBankTxnEntity> findAllByOwnerUserIdAndAccountIdIsNullOrderByIdDesc(Long ownerUserId);

  /**
   * Every account's movement total in one grouped query, so listing N accounts stays a single
   * round trip instead of N balance lookups.
   */
  @Query(
      """
      SELECT t.accountId, SUM(CASE WHEN t.direction = 'in' THEN t.amount ELSE -t.amount END)
      FROM CashBankTxnEntity t
      WHERE t.ownerUserId = :ownerUserId AND t.accountId IS NOT NULL
      GROUP BY t.accountId
      """)
  List<Object[]> sumByAccount(@Param("ownerUserId") Long ownerUserId);

  /** Net cash in hand, computed in the database rather than by loading the ledger. */
  @Query(
      """
      SELECT COALESCE(SUM(CASE WHEN t.direction = 'in' THEN t.amount ELSE -t.amount END), 0)
      FROM CashBankTxnEntity t
      WHERE t.ownerUserId = :ownerUserId AND t.accountId IS NULL
      """)
  BigDecimal sumCash(@Param("ownerUserId") Long ownerUserId);
}
