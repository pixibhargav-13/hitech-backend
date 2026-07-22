package com.hitech.erp.vyapar.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * One movement of money. A null {@code accountId} means the movement is against cash in hand,
 * which is why a transfer between bank and cash is stored as two rows sharing a transfer group.
 */
@Getter
@Setter
@Entity
@Table(name = "vyapar_cash_bank_txns")
public class CashBankTxnEntity extends BaseEntity {

  @Column(name = "owner_user_id", nullable = false)
  private Long ownerUserId;

  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "txn_type", nullable = false, length = 40)
  private String txnType;

  @Column(length = 200)
  private String name;

  @Column(name = "txn_date", length = 30)
  private String txnDate;

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal amount = BigDecimal.ZERO;

  /** "in" or "out". */
  @Column(nullable = false, length = 4)
  private String direction = "in";

  @Column(length = 500)
  private String note;

  @Column(name = "transfer_group", length = 40)
  private String transferGroup;
}
