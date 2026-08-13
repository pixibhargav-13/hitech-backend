package com.hitech.erp.vyapar.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** One line of a document's audit trail — what backs Vyapar's "View History" row action. */
@Getter
@Setter
@Entity
@Table(name = "vyapar_invoice_history")
public class InvoiceHistoryEntity extends BaseEntity {

  @Column(name = "invoice_id", nullable = false)
  private Long invoiceId;

  /** CREATED, EDITED, CANCELLED, REOPENED, PAYMENT, DUPLICATED, CONVERTED. */
  @Column(nullable = false, length = 30)
  private String action;

  @Column(length = 500)
  private String detail;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "at", nullable = false)
  private LocalDateTime at = LocalDateTime.now();
}
