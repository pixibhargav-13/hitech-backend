package com.hitech.erp.vyapar.service;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.vyapar.db.BankAccountEntity;
import com.hitech.erp.vyapar.db.BankAccountRepository;
import com.hitech.erp.vyapar.db.CashBankTxnEntity;
import com.hitech.erp.vyapar.db.CashBankTxnRepository;
import com.hitech.erp.vyapar.db.ChequeEntity;
import com.hitech.erp.vyapar.db.ChequeRepository;
import com.hitech.erp.vyapar.db.FirmProfileEntity;
import com.hitech.erp.vyapar.db.FirmProfileRepository;
import com.hitech.erp.vyapar.db.InvoiceEntity;
import com.hitech.erp.vyapar.db.InvoiceRepository;
import com.hitech.erp.vyapar.db.LoanAccountEntity;
import com.hitech.erp.vyapar.db.LoanAccountRepository;
import com.hitech.erp.vyapar.db.PaymentEntity;
import com.hitech.erp.vyapar.db.PaymentRepository;
import com.hitech.erp.vyapar.dto.CashBankDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cash &amp; Bank: bank accounts, the cash drawer, cheques and loans. Every row is private to the
 * user who created it. An account's balance is the sum of three things: its manual entries in
 * {@code vyapar_cash_bank_txns}, the receipts banked into it, and the bills settled through it.
 * Each is a grouped aggregate rather than a sum in Java, so listing accounts stays three queries
 * no matter how long the ledgers grow.
 */
@Service
@RequiredArgsConstructor
public class CashBankService {

  private final BankAccountRepository bankAccountRepository;
  private final CashBankTxnRepository txnRepository;
  private final ChequeRepository chequeRepository;
  private final LoanAccountRepository loanAccountRepository;
  private final FirmProfileRepository firmProfileRepository;
  private final PaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;

  /** A logo this big already prints crisply on an A4 header; anything more just bloats every load. */
  private static final int MAX_LOGO_CHARS = 400_000;

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static BigDecimal money(BigDecimal v) {
    return nz(v).setScale(2, RoundingMode.HALF_UP);
  }

  // ================= Bank accounts =================

  /**
   * Every account's net movement, from all three things that move its money.
   *
   * The bank screen used to read only `vyapar_cash_bank_txns` — the manual deposit / withdraw /
   * transfer entries. Nothing else ever writes there, so a receipt banked into HI-TECH CONSTRUCTION
   * left that account showing zero, which is why every balance on the Banks screen was ₹0.000.
   * Receipts and counter-paid bills are now summed in as well.
   *
   * Derived rather than written through: a posted copy would have to be kept in step with every
   * edit, cancel and delete of the underlying document, and it fixes the already-migrated books
   * with no backfill.
   */
  private Map<Long, BigDecimal> accountMovements(Long ownerUserId, Long projectId) {
    Map<Long, BigDecimal> sums = new HashMap<>();
    // Manual deposits and withdrawals carry no project — they are adjustments to the account
    // itself, not site spend — so they only count towards the unscoped "all projects" figure.
    if (projectId == null) {
      for (Object[] row : txnRepository.sumByAccount(ownerUserId)) {
        sums.merge((Long) row[0], (BigDecimal) row[1], BigDecimal::add);
      }
    }
    List<Object[]> payments =
        projectId == null ? paymentRepository.sumByBankAccount() : paymentRepository.sumByBankAccountForProject(projectId);
    for (Object[] row : payments) {
      sums.merge((Long) row[0], (BigDecimal) row[1], BigDecimal::add);
    }
    List<Object[]> docs =
        projectId == null
            ? invoiceRepository.sumPaidByBankAccount()
            : invoiceRepository.sumPaidByBankAccountForProject(projectId);
    for (Object[] row : docs) {
      sums.merge((Long) row[0], (BigDecimal) row[1], BigDecimal::add);
    }
    return sums;
  }

  @Transactional(readOnly = true)
  public List<BankAccountResponse> getBankAccounts(Long ownerUserId, Long projectId) {
    List<BankAccountEntity> accounts = bankAccountRepository.findAllByOwnerUserIdOrderByNameAsc(ownerUserId);
    Map<Long, BigDecimal> sums = accountMovements(ownerUserId, projectId);
    // The accounts themselves are payment methods shared across every site, so the list never
    // shrinks with the scope — only the money attributed to each does.
    return accounts.stream()
        .map(a -> toBankAccount(a, sums.getOrDefault(a.getId(), BigDecimal.ZERO), projectId))
        .toList();
  }

  @Transactional
  public BankAccountResponse createBankAccount(Long ownerUserId, BankAccountRequest r) {
    BankAccountEntity a = new BankAccountEntity();
    a.setOwnerUserId(ownerUserId);
    applyBankAccount(a, r);
    return toBankAccount(bankAccountRepository.save(a), BigDecimal.ZERO, null);
  }

  @Transactional
  public BankAccountResponse updateBankAccount(Long ownerUserId, Long id, BankAccountRequest r) {
    BankAccountEntity a = requireBankAccount(ownerUserId, id);
    applyBankAccount(a, r);
    BigDecimal sum = accountMovements(ownerUserId, null).getOrDefault(id, BigDecimal.ZERO);
    return toBankAccount(bankAccountRepository.save(a), sum, null);
  }

  @Transactional
  public void deleteBankAccount(Long ownerUserId, Long id) {
    bankAccountRepository.delete(requireBankAccount(ownerUserId, id));
  }

  /**
   * One account's ledger: manual entries, receipts banked into it, and bills settled through it,
   * newest first. Same reasoning as {@link #accountMovements} — the screen said "No transactions
   * yet" on accounts that had years of receipts against them, because only the manual entries were
   * ever being read.
   *
   * The synthetic rows reuse the document's own id so the row still deep-links to it: `txnHref`
   * on the frontend maps the type label ("Sale", "Payment-In") plus the id onto a route.
   */
  @Transactional(readOnly = true)
  public List<CashBankTxnResponse> getAccountTxns(Long ownerUserId, Long id, Long projectId) {
    requireBankAccount(ownerUserId, id);
    List<CashBankTxnResponse> rows = new ArrayList<>();
    // Manual entries have no project, so they appear only in the unscoped view. See accountMovements.
    if (projectId == null) {
      rows.addAll(
          txnRepository.findAllByOwnerUserIdAndAccountIdOrderByIdDesc(ownerUserId, id).stream().map(this::toTxn).toList());
    }

    List<PaymentEntity> pays =
        projectId == null
            ? paymentRepository.findByBankAccountIdOrderByIdDesc(id)
            : paymentRepository.findByBankAccountIdAndProjectIdOrderByIdDesc(id, projectId);
    for (PaymentEntity p : pays) {
      boolean in = "IN".equalsIgnoreCase(p.getDirection());
      rows.add(new CashBankTxnResponse(
          p.getId(),
          id,
          in ? "Payment-In" : "Payment-Out",
          p.getParty() == null ? null : p.getParty().getName(),
          p.getPaymentDate(),
          money(p.getAmount()),
          in ? "in" : "out",
          p.getReference()));
    }

    List<InvoiceEntity> docs =
        projectId == null
            ? invoiceRepository.findSettledByBankAccount(id)
            : invoiceRepository.findSettledByBankAccountAndProject(id, projectId);
    for (InvoiceEntity i : docs) {
      boolean in = "SALE".equals(i.getDocType()) || "PURCHASE_RETURN".equals(i.getDocType());
      rows.add(new CashBankTxnResponse(
          i.getId(),
          id,
          docLabel(i.getDocType()),
          i.getParty() == null ? i.getBillingName() : i.getParty().getName(),
          i.getInvoiceDate(),
          money(i.getPaidAmount()),
          in ? "in" : "out",
          i.getInvoiceNo()));
    }

    // Newest first by date; the ledger reads chronologically regardless of which table a row is
    // from, and a null date sinks rather than blowing up the comparator.
    rows.sort(Comparator.comparing(
        (CashBankTxnResponse t) -> t.date() == null ? "" : t.date(), Comparator.reverseOrder()));
    return rows;
  }

  /** The document-type label the ledgers and the frontend's `txnHref` route table agree on. */
  private static String docLabel(String docType) {
    return switch (docType) {
      case "SALE" -> "Sale";
      case "PURCHASE" -> "Purchase";
      case "SALE_RETURN" -> "Credit Note";
      case "PURCHASE_RETURN" -> "Debit Note";
      case "EXPENSE" -> "Expense";
      default -> docType;
    };
  }

  private BankAccountEntity requireBankAccount(Long ownerUserId, Long id) {
    return bankAccountRepository
        .findByIdAndOwnerUserId(id, ownerUserId)
        .orElseThrow(() -> new EntityNotFoundException("Bank account " + id + " not found"));
  }

  private void applyBankAccount(BankAccountEntity a, BankAccountRequest r) {
    a.setName(r.name().trim());
    if (r.openingBalance() != null) a.setOpeningBalance(money(r.openingBalance()));
    a.setOpeningDate(r.openingDate());
    a.setAccountNumber(r.accountNumber());
    a.setIfsc(r.ifsc());
    a.setBankName(r.bankName());
    a.setAccountHolder(r.accountHolder());
    a.setUpiId(r.upiId());
    if (r.printUpiQr() != null) a.setPrintUpiQr(r.printUpiQr());
    if (r.printBankDetails() != null) a.setPrintBankDetails(r.printBankDetails());
    if (r.isActive() != null) a.setActive(r.isActive());
  }

  /**
   * @param projectId when set, the opening balance is left out of the figure: an account's opening
   *     float belongs to the account, not to any one site, so adding it to every project's view
   *     would count the same money once per project.
   */
  private BankAccountResponse toBankAccount(BankAccountEntity a, BigDecimal txnSum, Long projectId) {
    return new BankAccountResponse(
        a.getId(),
        a.getName(),
        a.getOpeningBalance(),
        a.getOpeningDate(),
        a.getAccountNumber(),
        a.getIfsc(),
        a.getBankName(),
        a.getAccountHolder(),
        a.getUpiId(),
        a.isPrintUpiQr(),
        a.isPrintBankDetails(),
        a.isActive(),
        money(projectId == null ? a.getOpeningBalance().add(nz(txnSum)) : nz(txnSum)));
  }

  // ================= Cash in hand =================

  @Transactional(readOnly = true)
  public List<CashBankTxnResponse> getCashTxns(Long ownerUserId) {
    return txnRepository.findAllByOwnerUserIdAndAccountIdIsNullOrderByIdDesc(ownerUserId).stream().map(this::toTxn).toList();
  }

  // ================= Entries (transfers & adjustments) =================

  @Transactional
  public CashBankTxnResponse postCashBankEntry(Long ownerUserId, CashBankEntryRequest r) {
    BigDecimal amount = money(r.amount());
    String date = r.date() != null ? r.date() : LocalDate.now().toString();
    String group = UUID.randomUUID().toString();

    CashBankTxnEntity primary =
        switch (r.kind()) {
          case "BANK_TO_CASH" -> {
            requireBankAccount(ownerUserId, r.fromAccountId());
            save(ownerUserId, r.fromAccountId(), "Cash Withdrawal", date, amount, "out", r.description(), group);
            yield save(ownerUserId, null, "Cash Deposit", date, amount, "in", r.description(), group);
          }
          case "CASH_TO_BANK" -> {
            requireBankAccount(ownerUserId, r.toAccountId());
            save(ownerUserId, null, "Cash Withdrawal", date, amount, "out", r.description(), group);
            yield save(ownerUserId, r.toAccountId(), "Cash Deposit", date, amount, "in", r.description(), group);
          }
          case "BANK_TO_BANK" -> {
            requireBankAccount(ownerUserId, r.fromAccountId());
            requireBankAccount(ownerUserId, r.toAccountId());
            save(ownerUserId, r.fromAccountId(), "Bank Transfer Out", date, amount, "out", r.description(), group);
            yield save(ownerUserId, r.toAccountId(), "Bank Transfer In", date, amount, "in", r.description(), group);
          }
          case "ADJUST_BANK" -> {
            requireBankAccount(ownerUserId, r.fromAccountId());
            String dir = "REDUCE".equalsIgnoreCase(r.mode()) ? "out" : "in";
            yield save(ownerUserId, r.fromAccountId(), "Balance Adjustment", date, amount, dir, r.description(), null);
          }
          case "ADJUST_CASH" -> {
            String dir = "REDUCE".equalsIgnoreCase(r.mode()) ? "out" : "in";
            yield save(ownerUserId, null, "Cash Adjustment", date, amount, dir, r.description(), null);
          }
          default -> throw new IllegalArgumentException("Unknown entry kind: " + r.kind());
        };
    return toTxn(primary);
  }

  private CashBankTxnEntity save(
      Long ownerUserId, Long accountId, String type, String date, BigDecimal amount, String direction, String note, String group) {
    CashBankTxnEntity t = new CashBankTxnEntity();
    t.setOwnerUserId(ownerUserId);
    t.setAccountId(accountId);
    t.setTxnType(type);
    t.setName(note);
    t.setTxnDate(date);
    t.setAmount(amount);
    t.setDirection(direction);
    t.setNote(note);
    t.setTransferGroup(group);
    return txnRepository.save(t);
  }

  private CashBankTxnResponse toTxn(CashBankTxnEntity t) {
    return new CashBankTxnResponse(t.getId(), t.getAccountId(), t.getTxnType(), t.getName(), t.getTxnDate(), t.getAmount(), t.getDirection(), t.getNote());
  }

  // ================= Cheques =================

  @Transactional(readOnly = true)
  public List<ChequeResponse> getCheques(Long ownerUserId) {
    return chequeRepository.findAllByOwnerUserIdOrderByIdDesc(ownerUserId).stream().map(this::toCheque).toList();
  }

  @Transactional
  public ChequeResponse updateCheque(Long ownerUserId, Long id, ChequeUpdateRequest r) {
    ChequeEntity c =
        chequeRepository.findByIdAndOwnerUserId(id, ownerUserId).orElseThrow(() -> new EntityNotFoundException("Cheque " + id + " not found"));
    if (r.status() != null) c.setStatus(r.status());
    if (r.transferDate() != null) c.setTransferDate(r.transferDate());
    if (r.accountId() != null) c.setAccountId(r.accountId());
    return toCheque(chequeRepository.save(c));
  }

  private ChequeResponse toCheque(ChequeEntity c) {
    return new ChequeResponse(
        c.getId(), c.getChequeNo(), c.getPartyName(), c.getInvoiceNo(), c.getDirection(), c.getAmount(), c.getChequeDate(), c.getTransferDate(), c.getStatus(), c.getAccountId());
  }

  // ================= Loans =================

  @Transactional(readOnly = true)
  public List<LoanAccountResponse> getLoanAccounts(Long ownerUserId) {
    return loanAccountRepository.findAllByOwnerUserIdOrderByIdDesc(ownerUserId).stream().map(this::toLoan).toList();
  }

  @Transactional
  public LoanAccountResponse createLoanAccount(Long ownerUserId, LoanAccountRequest r) {
    LoanAccountEntity l = new LoanAccountEntity();
    l.setOwnerUserId(ownerUserId);
    l.setName(r.name().trim());
    l.setLender(r.lender());
    l.setAccountNumber(r.accountNumber());
    l.setLoanAmount(money(r.loanAmount()));
    l.setBalance(money(r.balance() != null ? r.balance() : r.loanAmount()));
    l.setInterestRate(nz(r.interestRate()));
    l.setTermMonths(r.termMonths() != null ? r.termMonths() : 0);
    l.setStartDate(r.startDate());
    l.setEmiAmount(money(r.emiAmount()));
    return toLoan(loanAccountRepository.save(l));
  }

  private LoanAccountResponse toLoan(LoanAccountEntity l) {
    return new LoanAccountResponse(
        l.getId(), l.getName(), l.getLender(), l.getAccountNumber(), l.getLoanAmount(), l.getBalance(), l.getInterestRate(), l.getTermMonths(), l.getStartDate(), l.getEmiAmount());
  }

  // ================= Firm profile =================

  @Transactional(readOnly = true)
  public FirmProfileResponse getFirmProfile(Long ownerUserId) {
    return firmProfileRepository
        .findByOwnerUserId(ownerUserId)
        .map(p -> new FirmProfileResponse(p.getBusinessName(), p.getAddress(), p.getPhone(), p.getEmail(), p.getGstin(), p.getState(), p.getLogoDataUrl(), p.getFooterNote()))
        .orElse(new FirmProfileResponse(null, null, null, null, null, null, null, null));
  }

  @Transactional
  public FirmProfileResponse updateFirmProfile(Long ownerUserId, FirmProfileRequest r) {
    FirmProfileEntity p = firmProfileRepository.findByOwnerUserId(ownerUserId).orElseGet(FirmProfileEntity::new);
    p.setOwnerUserId(ownerUserId);
    p.setBusinessName(r.businessName());
    p.setAddress(r.address());
    p.setPhone(r.phone());
    p.setEmail(r.email());
    p.setGstin(r.gstin());
    p.setState(r.state());
    if (r.logoDataUrl() != null && r.logoDataUrl().length() > MAX_LOGO_CHARS) {
      throw new IllegalArgumentException("Logo is too large — please use a smaller image.");
    }
    p.setLogoDataUrl(r.logoDataUrl());
    p.setFooterNote(r.footerNote());
    FirmProfileEntity saved = firmProfileRepository.save(p);
    return new FirmProfileResponse(
        saved.getBusinessName(), saved.getAddress(), saved.getPhone(), saved.getEmail(), saved.getGstin(), saved.getState(), saved.getLogoDataUrl(), saved.getFooterNote());
  }
}
