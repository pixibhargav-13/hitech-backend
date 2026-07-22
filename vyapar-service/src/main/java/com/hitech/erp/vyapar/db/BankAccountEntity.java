package com.hitech.erp.vyapar.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** A bank account the firm keeps money in. Private to the user who created it. */
@Getter
@Setter
@Entity
@Table(name = "vyapar_bank_accounts")
public class BankAccountEntity extends BaseEntity {

  @Column(name = "owner_user_id", nullable = false)
  private Long ownerUserId;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(name = "opening_balance", nullable = false, precision = 16, scale = 2)
  private BigDecimal openingBalance = BigDecimal.ZERO;

  @Column(name = "opening_date", length = 30)
  private String openingDate;

  @Column(name = "account_number", length = 60)
  private String accountNumber;

  @Column(length = 20)
  private String ifsc;

  @Column(name = "bank_name", length = 160)
  private String bankName;

  @Column(name = "account_holder", length = 160)
  private String accountHolder;

  @Column(name = "upi_id", length = 120)
  private String upiId;

  @Column(name = "print_upi_qr", nullable = false)
  private boolean printUpiQr = false;

  @Column(name = "print_bank_details", nullable = false)
  private boolean printBankDetails = false;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;
}
