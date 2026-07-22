package com.hitech.erp.vyapar.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** A cheque received from or issued to a party, and where it has got to. */
@Getter
@Setter
@Entity
@Table(name = "vyapar_cheques")
public class ChequeEntity extends BaseEntity {

  @Column(name = "owner_user_id", nullable = false)
  private Long ownerUserId;

  @Column(name = "cheque_no", nullable = false, length = 60)
  private String chequeNo;

  @Column(name = "party_name", length = 200)
  private String partyName;

  @Column(name = "invoice_no", length = 60)
  private String invoiceNo;

  /** IN = received, OUT = issued. */
  @Column(nullable = false, length = 4)
  private String direction = "IN";

  @Column(nullable = false, precision = 16, scale = 2)
  private BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "cheque_date", length = 30)
  private String chequeDate;

  @Column(name = "transfer_date", length = 30)
  private String transferDate;

  /** OPEN, DEPOSITED, WITHDRAWN or REOPENED. */
  @Column(nullable = false, length = 20)
  private String status = "OPEN";

  @Column(name = "account_id")
  private Long accountId;
}
