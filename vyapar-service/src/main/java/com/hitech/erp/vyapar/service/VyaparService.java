package com.hitech.erp.vyapar.service;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.vyapar.db.InvoiceEntity;
import com.hitech.erp.vyapar.db.InvoiceLineEntity;
import com.hitech.erp.vyapar.db.ItemEntity;
import com.hitech.erp.vyapar.db.PartyEntity;
import com.hitech.erp.vyapar.db.PaymentEntity;
import com.hitech.erp.vyapar.db.InvoiceHistoryEntity;
import com.hitech.erp.vyapar.db.PaymentLinkEntity;
import com.hitech.erp.vyapar.db.StockAdjustmentEntity;
import com.hitech.erp.vyapar.db.VyaparSettingsEntity;
import com.hitech.erp.vyapar.db.InvoiceHistoryRepository;
import com.hitech.erp.vyapar.db.InvoiceRepository;
import com.hitech.erp.vyapar.db.ItemRepository;
import com.hitech.erp.vyapar.db.PartyRepository;
import com.hitech.erp.vyapar.db.PaymentLinkRepository;
import com.hitech.erp.vyapar.db.PaymentRepository;
import com.hitech.erp.vyapar.db.StockAdjustmentRepository;
import com.hitech.erp.vyapar.db.VyaparSettingsRepository;
import com.hitech.erp.vyapar.dto.VyaparDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The whole Vyapar domain. Documents drive everything: a SALE increases what a customer owes and
 * decreases stock; a PURCHASE does the reverse. Party balances and stock are derived from posted
 * documents rather than stored, so the books can never drift out of sync with their evidence.
 */
@Service
@RequiredArgsConstructor
public class VyaparService {

  private final PartyRepository partyRepository;
  private final ItemRepository itemRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentRepository paymentRepository;
  private final StockAdjustmentRepository stockAdjustmentRepository;
  private final PaymentLinkRepository paymentLinkRepository;
  private final InvoiceHistoryRepository invoiceHistoryRepository;
  private final VyaparSettingsRepository settingsRepository;

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  /** Documents that represent real money owed/owing (estimates and orders don't). */
  private static final List<String> POSTED = List.of("SALE", "PURCHASE", "SALE_RETURN", "PURCHASE_RETURN");

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static BigDecimal money(BigDecimal v) {
    return nz(v).setScale(2, RoundingMode.HALF_UP);
  }

  // ================= Parties =================

  @Transactional(readOnly = true)
  public List<PartyResponse> getParties(String type, Long projectId) {
    List<PartyEntity> parties =
        (type == null || type.isBlank())
            ? partyRepository.findAllByOrderByNameAsc()
            : partyRepository.findAllByPartyTypeOrderByNameAsc(type.toUpperCase());
    // Parties are global master data — always listed. Only their derived balances follow the
    // selected project scope (via the documents/payments booked against them).
    Map<Long, BigDecimal> balances = balancesByParty(projectId);
    return parties.stream().map(p -> toParty(p, balances)).toList();
  }

  @Transactional(readOnly = true)
  public PartyResponse getParty(Long id) {
    return toParty(requireParty(id), balanceOfParty(id, null));
  }

  @Transactional
  public PartyResponse createParty(PartyRequest r) {
    PartyEntity p = new PartyEntity();
    applyParty(p, r);
    PartyEntity saved = partyRepository.save(p);
    return toParty(saved, balanceOfParty(saved.getId(), null));
  }

  @Transactional
  public PartyResponse updateParty(Long id, PartyRequest r) {
    PartyEntity p = requireParty(id);
    applyParty(p, r);
    return toParty(partyRepository.save(p), balanceOfParty(id, null));
  }

  @Transactional
  public void deleteParty(Long id) {
    partyRepository.delete(requireParty(id));
  }

  private void applyParty(PartyEntity p, PartyRequest r) {
    p.setName(r.name().trim());
    if (r.partyType() != null) p.setPartyType(r.partyType());
    p.setPhone(r.phone());
    p.setEmail(r.email());
    p.setGstin(r.gstin());
    p.setGstType(r.gstType());
    p.setState(r.state());
    p.setBillingAddress(r.billingAddress());
    p.setShippingAddress(r.shippingAddress());
    p.setCity(r.city());
    p.setPartyGroup(r.partyGroup());
    if (r.openingBalance() != null) p.setOpeningBalance(money(r.openingBalance()));
    p.setOpeningDate(r.openingDate());
    // null clears the limit; a value caps how much the party may owe.
    p.setCreditLimit(r.creditLimit() == null ? null : money(r.creditLimit()));
    p.setField1(r.field1());
    p.setField2(r.field2());
    p.setField3(r.field3());
    p.setField4(r.field4());
    if (r.isActive() != null) p.setActive(r.isActive());
    if (r.bankAccountId() != null) p.setBankAccountId(r.bankAccountId());
  }

  /**
   * A party's ledger: every posted document and payment for them, newest first, with the
   * outstanding balance carried on each document.
   */
  @Transactional(readOnly = true)
  public List<PartyLedgerRow> partyLedger(Long partyId) {
    List<PartyLedgerRow> rows = new ArrayList<>();

    // Only this party's rows — previously every invoice and payment in the database was loaded
    // and filtered in Java, so opening a ledger cost O(all documents).
    List<InvoiceEntity> invoices = invoiceRepository.findByParty_IdOrderByIdDesc(partyId);
    List<PaymentEntity> payments = paymentRepository.findByParty_IdOrderByIdDesc(partyId);

    for (InvoiceEntity inv : invoices) {
      BigDecimal balance = money(nz(inv.getTotal()).subtract(nz(inv.getPaidAmount())));
      rows.add(new PartyLedgerRow(
          inv.getId(),
          "INVOICE",
          docLabel(inv.getDocType()),
          inv.getInvoiceNo(),
          inv.getInvoiceDate(),
          money(inv.getTotal()),
          inv.isCancelled() ? BigDecimal.ZERO : balance,
          inv.isCancelled()
              ? "Cancelled"
              : balance.compareTo(BigDecimal.ZERO) <= 0 ? "Paid" : "Unpaid"));
    }

    // Vyapar shows a payment's *unused* portion in the Balance/Unused column, so we need each
    // payment's links. One bulk query for the whole ledger rather than one per row.
    Map<Long, BigDecimal> linkedByPayment = linkedAmountsByPayment(payments.stream().map(PaymentEntity::getId).toList());

    for (PaymentEntity pay : payments) {
      BigDecimal amount = money(pay.getAmount());
      BigDecimal linked = money(linkedByPayment.getOrDefault(pay.getId(), BigDecimal.ZERO));
      BigDecimal unused = money(amount.subtract(linked).max(BigDecimal.ZERO));
      rows.add(new PartyLedgerRow(
          pay.getId(),
          "PAYMENT",
          "IN".equals(pay.getDirection()) ? "Payment-In" : "Payment-Out",
          pay.getReference(),
          pay.getPaymentDate(),
          amount,
          unused,
          unused.compareTo(BigDecimal.ZERO) <= 0
              ? "Used"
              : linked.compareTo(BigDecimal.ZERO) > 0 ? "Partially Used" : "Unused"));
    }

    // Newest first; blank dates sink to the bottom.
    rows.sort((a, b) -> {
      String da = a.date() == null ? "" : a.date();
      String db = b.date() == null ? "" : b.date();
      return db.compareTo(da);
    });
    return rows;
  }

  /** Bulk create from an imported sheet; blank names are skipped. */
  @Transactional
  public List<PartyResponse> importParties(List<PartyRequest> rows) {
    List<PartyEntity> saved = new ArrayList<>();
    for (PartyRequest r : rows) {
      if (r.name() == null || r.name().isBlank()) continue;
      PartyEntity p = new PartyEntity();
      applyParty(p, r);
      saved.add(partyRepository.save(p));
    }
    Map<Long, BigDecimal> balances = balancesByParty(null);
    return saved.stream().map(p -> toParty(p, balances)).toList();
  }

  private static String docLabel(String docType) {
    if (docType == null) return "Document";
    return switch (docType) {
      case "SALE" -> "Sale";
      case "PURCHASE" -> "Purchase";
      case "SALE_RETURN" -> "Credit Note";
      case "PURCHASE_RETURN" -> "Debit Note";
      case "ESTIMATE" -> "Estimate";
      case "PROFORMA" -> "Proforma";
      case "SALE_ORDER" -> "Sale Order";
      case "PURCHASE_ORDER" -> "Purchase Order";
      case "DELIVERY_CHALLAN" -> "Delivery Challan";
      case "EXPENSE" -> "Expense";
      default -> docType;
    };
  }

  private PartyResponse toParty(PartyEntity p, Map<Long, BigDecimal> balances) {
    BigDecimal derived = balances.getOrDefault(p.getId(), BigDecimal.ZERO);
    return new PartyResponse(
        p.getId(),
        p.getName(),
        p.getPartyType(),
        p.getPhone(),
        p.getEmail(),
        p.getGstin(),
        p.getGstType(),
        p.getState(),
        p.getBillingAddress(),
        p.getShippingAddress(),
        p.getCity(),
        p.getPartyGroup(),
        money(p.getOpeningBalance()),
        p.getOpeningDate(),
        p.getCreditLimit() == null ? null : money(p.getCreditLimit()),
        p.getField1(), p.getField2(), p.getField3(), p.getField4(),
        p.isActive(),
        p.getBankAccountId(),
        money(nz(p.getOpeningBalance()).add(derived)));
  }

  /**
   * Net position per party: sales add to what they owe, purchases subtract, payments settle.
   * Positive = receivable, negative = payable.
   */
  private Map<Long, BigDecimal> balancesByParty(Long projectId) {
    Map<Long, BigDecimal> out = new LinkedHashMap<>();
    for (InvoiceEntity inv : invoiceRepository.findAll()) {
      if (inv.getParty() == null || !POSTED.contains(inv.getDocType())) continue;
      if (inv.isCancelled()) continue; // a cancelled document owes nothing
      if (!inScope(inv.getProjectId(), projectId)) continue;
      BigDecimal outstanding = nz(inv.getTotal()).subtract(nz(inv.getPaidAmount()));
      BigDecimal signed =
          switch (inv.getDocType()) {
            case "SALE" -> outstanding;
            case "PURCHASE" -> outstanding.negate();
            case "SALE_RETURN" -> outstanding.negate();
            case "PURCHASE_RETURN" -> outstanding;
            default -> BigDecimal.ZERO;
          };
      out.merge(inv.getParty().getId(), signed, BigDecimal::add);
    }
    // A payment counts here only for the part of it that isn't linked to a document.
    //
    // The linked part has already moved that document's paidAmount, and the loop above derives the
    // document's contribution from total − paidAmount — so counting the whole payment again would
    // settle the same money twice. (The old code sidestepped this by never linking a payment at
    // all; now that Link Payment exists, the arithmetic has to be right.) An unlinked amount is a
    // genuine advance and does reduce what the party owes.
    Map<Long, BigDecimal> linkedByPayment = new LinkedHashMap<>();
    for (PaymentLinkEntity link : paymentLinkRepository.findAll()) {
      linkedByPayment.merge(link.getPaymentId(), nz(link.getAmount()), BigDecimal::add);
    }
    for (PaymentEntity pay : paymentRepository.findAll()) {
      if (pay.getParty() == null) continue;
      if (!inScope(pay.getProjectId(), projectId)) continue;
      BigDecimal unused = unusedOf(pay, linkedByPayment);
      if (unused.compareTo(BigDecimal.ZERO) == 0) continue;
      // Money in reduces a receivable; money out reduces a payable.
      BigDecimal signed = "IN".equals(pay.getDirection()) ? unused.negate() : unused;
      out.merge(pay.getParty().getId(), signed, BigDecimal::add);
    }
    return out;
  }

  /** How much of a payment is still sitting unapplied. */
  private static BigDecimal unusedOf(PaymentEntity pay, Map<Long, BigDecimal> linkedByPayment) {
    BigDecimal linked = nz(linkedByPayment.get(pay.getId()));
    return nz(pay.getAmount()).subtract(linked).max(BigDecimal.ZERO);
  }

  /**
   * The same arithmetic as {@link #balancesByParty} but for one party only.
   *
   * <p>Reading or saving a single party used to recompute every party's balance from every
   * document in the books — O(all documents) to answer a question about one row. This touches only
   * that party's documents, via the indexed party_id lookups.
   */
  private Map<Long, BigDecimal> balanceOfParty(Long partyId, Long projectId) {
    BigDecimal sum = BigDecimal.ZERO;
    for (InvoiceEntity inv : invoiceRepository.findByParty_IdOrderByIdDesc(partyId)) {
      if (!POSTED.contains(inv.getDocType()) || inv.isCancelled()) continue;
      if (!inScope(inv.getProjectId(), projectId)) continue;
      BigDecimal outstanding = nz(inv.getTotal()).subtract(nz(inv.getPaidAmount()));
      sum = sum.add(
          switch (inv.getDocType()) {
            case "SALE", "PURCHASE_RETURN" -> outstanding;
            case "PURCHASE", "SALE_RETURN" -> outstanding.negate();
            default -> BigDecimal.ZERO;
          });
    }
    List<PaymentEntity> payments = paymentRepository.findByParty_IdOrderByIdDesc(partyId);
    Map<Long, BigDecimal> linkedByPayment =
        linkedAmountsByPayment(payments.stream().map(PaymentEntity::getId).toList());
    for (PaymentEntity pay : payments) {
      if (!inScope(pay.getProjectId(), projectId)) continue;
      // Only the unapplied part — see the note in balancesByParty.
      BigDecimal unused = unusedOf(pay, linkedByPayment);
      sum = sum.add("IN".equals(pay.getDirection()) ? unused.negate() : unused);
    }
    Map<Long, BigDecimal> out = new LinkedHashMap<>();
    out.put(partyId, sum);
    return out;
  }

  /**
   * Total linked amount per payment, for a batch of payments — one query for the whole page
   * instead of one per row.
   */
  private Map<Long, BigDecimal> linkedAmountsByPayment(List<Long> paymentIds) {
    Map<Long, BigDecimal> out = new LinkedHashMap<>();
    if (paymentIds.isEmpty()) return out;
    for (PaymentLinkEntity link : paymentLinkRepository.findByPaymentIdIn(paymentIds)) {
      out.merge(link.getPaymentId(), nz(link.getAmount()), BigDecimal::add);
    }
    return out;
  }

  // ================= Items =================

  @Transactional(readOnly = true)
  public List<ItemResponse> getItems(Long projectId) {
    // Items and their stock are global master data — shared across every project, never scoped.
    return itemRepository.findAllByOrderByNameAsc().stream().map(this::toItem).toList();
  }

  @Transactional
  public ItemResponse createItem(ItemRequest r) {
    ItemEntity i = new ItemEntity();
    applyItem(i, r);
    return toItem(itemRepository.save(i));
  }

  /** Bulk create from an imported sheet; blank names are skipped. */
  @Transactional
  public List<ItemResponse> importItems(List<ItemRequest> rows) {
    List<ItemResponse> saved = new ArrayList<>();
    for (ItemRequest r : rows) {
      if (r.name() == null || r.name().isBlank()) continue;
      ItemEntity i = new ItemEntity();
      applyItem(i, r);
      saved.add(toItem(itemRepository.save(i)));
    }
    return saved;
  }

  @Transactional
  public ItemResponse updateItem(Long id, ItemRequest r) {
    ItemEntity i = requireItem(id);
    applyItem(i, r);
    return toItem(itemRepository.save(i));
  }

  @Transactional
  public void deleteItem(Long id) {
    ItemEntity item = requireItem(id);
    // Its manual stock corrections reference it, so clear those first — otherwise the delete
    // fails on the foreign key and surfaces as an opaque 500.
    stockAdjustmentRepository.deleteAll(stockAdjustmentRepository.findAllByItemIdOrderByIdDesc(id));
    itemRepository.delete(item);
  }

  /**
   * An item's stock ledger: every sale/purchase line that touched it, plus manual adjustments,
   * newest first — mirrors partyLedger's shape.
   */
  @Transactional(readOnly = true)
  public List<ItemLedgerRow> itemLedger(Long itemId) {
    List<ItemLedgerRow> rows = new ArrayList<>();

    // A single join instead of walking every invoice and lazily loading its lines and party —
    // that was ~2 extra queries per invoice in the database, for an item that may appear on none.
    for (Object[] r : invoiceRepository.findItemLedgerRows(itemId)) {
      BigDecimal total = nz((BigDecimal) r[7]);
      BigDecimal paid = nz((BigDecimal) r[8]);
      boolean cancelled = Boolean.TRUE.equals(r[9]);
      BigDecimal balance = total.subtract(paid);
      rows.add(new ItemLedgerRow(
          (Long) r[0],
          docLabel((String) r[1]),
          (String) r[2],
          (String) r[3],
          (String) r[4],
          nz((BigDecimal) r[5]),
          money((BigDecimal) r[6]),
          cancelled
              ? "Cancelled"
              : balance.compareTo(BigDecimal.ZERO) <= 0
                  ? "Paid"
                  : paid.compareTo(BigDecimal.ZERO) > 0 ? "Partial" : "Unpaid"));
    }

    for (StockAdjustmentEntity adj : stockAdjustmentRepository.findAllByItemIdOrderByIdDesc(itemId)) {
      rows.add(new ItemLedgerRow(
          adj.getId(),
          "ADD".equals(adj.getMode()) ? "Stock Added" : "Stock Reduced",
          null,
          adj.getNote(),
          adj.getAdjDate(),
          nz(adj.getQuantity()),
          money(adj.getAtPrice()),
          null));
    }

    rows.sort((a, b) -> {
      String da = a.date() == null ? "" : a.date();
      String db = b.date() == null ? "" : b.date();
      return db.compareTo(da);
    });
    return rows;
  }

  private String statusOf(InvoiceEntity inv) {
    if (inv.isCancelled()) return "Cancelled";
    BigDecimal balance = nz(inv.getTotal()).subtract(nz(inv.getPaidAmount()));
    return balance.compareTo(BigDecimal.ZERO) <= 0
        ? "Paid"
        : nz(inv.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0 ? "Partial" : "Unpaid";
  }

  /** Manual stock correction — Vyapar's "Adjust Item". */
  @Transactional
  public ItemResponse adjustStock(Long itemId, StockAdjustRequest r) {
    ItemEntity i = requireItem(itemId);
    BigDecimal qty = nz(r.quantity());
    int direction = "REDUCE".equalsIgnoreCase(r.mode()) ? -1 : 1;
    i.setStockQty(nz(i.getStockQty()).add(qty.multiply(BigDecimal.valueOf(direction))));
    itemRepository.save(i);

    StockAdjustmentEntity adj = new StockAdjustmentEntity();
    adj.setItemId(itemId);
    adj.setBankAccountId(i.getBankAccountId());
    adj.setMode(direction < 0 ? "REDUCE" : "ADD");
    adj.setQuantity(qty);
    adj.setAtPrice(money(r.atPrice()));
    adj.setAdjDate(r.date() == null || r.date().isBlank() ? LocalDate.now().toString() : r.date());
    adj.setNote(r.note());
    stockAdjustmentRepository.save(adj);

    return toItem(i);
  }

  private void applyItem(ItemEntity i, ItemRequest r) {
    i.setName(r.name().trim());
    i.setCategory(r.category() == null || r.category().isBlank() ? null : r.category().trim());
    i.setDescription(r.description());
    i.setItemCode(r.itemCode());
    i.setHsn(r.hsn());
    if (r.bankAccountId() != null) i.setBankAccountId(r.bankAccountId());
    if (r.unit() != null && !r.unit().isBlank()) i.setUnit(r.unit());
    if (r.salePrice() != null) i.setSalePrice(r.salePrice());
    if (r.purchasePrice() != null) i.setPurchasePrice(r.purchasePrice());
    if (r.taxPercent() != null) i.setTaxPercent(r.taxPercent());
    if (r.stockQty() != null) i.setStockQty(r.stockQty());
    if (r.lowStockAlert() != null) i.setLowStockAlert(r.lowStockAlert());
    if (r.isService() != null) i.setService(r.isService());
    // An empty string clears the photo; null leaves whatever is already stored alone.
    if (r.imageDataUrl() != null) i.setImageDataUrl(r.imageDataUrl().isBlank() ? null : r.imageDataUrl());
    if (r.isActive() != null) i.setActive(r.isActive());
  }

  private ItemResponse toItem(ItemEntity i) {
    // A shelf that's gone negative is worth nothing, not a negative amount. Stock goes below zero
    // when consumption is recorded against something that was never booked in — the client's books
    // have nine such items — and multiplying that by a price would credit the balance sheet for
    // stock nobody has. Vyapar reports zero for every one of them, so we do too.
    BigDecimal value =
        nz(i.getStockQty()).max(BigDecimal.ZERO).multiply(nz(i.getPurchasePrice()));
    boolean low =
        !i.isService()
            && nz(i.getLowStockAlert()).compareTo(BigDecimal.ZERO) > 0
            && nz(i.getStockQty()).compareTo(nz(i.getLowStockAlert())) <= 0;
    return new ItemResponse(
        i.getId(), i.getName(), i.getCategory(), i.getDescription(), i.getItemCode(), i.getHsn(), i.getUnit(),
        money(i.getSalePrice()), money(i.getPurchasePrice()), nz(i.getTaxPercent()),
        nz(i.getStockQty()), nz(i.getLowStockAlert()), i.isService(), i.getImageDataUrl(), i.isActive(),
        i.getBankAccountId(), money(value), low);
  }

  // ================= Invoices =================

  @Transactional(readOnly = true)
  public List<InvoiceResponse> getInvoices(String docType, Long projectId) {
    List<InvoiceEntity> list =
        (docType == null || docType.isBlank())
            ? invoiceRepository.findAllByOrderByIdDesc()
            : invoiceRepository.findAllByDocTypeOrderByIdDesc(docType.toUpperCase());
    return list.stream()
        .filter(inv -> inScope(inv.getProjectId(), projectId))
        .map(this::toInvoice)
        .toList();
  }

  @Transactional(readOnly = true)
  public InvoiceResponse getInvoice(Long id) {
    return toInvoice(requireInvoice(id));
  }

  @Transactional
  public InvoiceResponse createInvoice(InvoiceRequest r, Long userId) {
    InvoiceEntity inv = new InvoiceEntity();
    String type = r.docType() == null || r.docType().isBlank() ? "SALE" : r.docType().toUpperCase();
    inv.setDocType(type);
    inv.setInvoiceNo(
        r.invoiceNo() == null || r.invoiceNo().isBlank() ? nextInvoiceNo(type) : r.invoiceNo());
    inv.setCreatedBy(userId);
    applyInvoice(inv, r);
    InvoiceEntity saved = invoiceRepository.save(inv);
    applyStock(saved, +1);
    history(saved.getId(), "CREATED", saved.getInvoiceNo(), userId);
    return toInvoice(saved);
  }

  @Transactional
  public InvoiceResponse updateInvoice(Long id, InvoiceRequest r) {
    InvoiceEntity inv = requireInvoice(id);
    // Roll back the old stock effect before re-applying the new one.
    applyStock(inv, -1);
    if (r.invoiceNo() != null && !r.invoiceNo().isBlank()) inv.setInvoiceNo(r.invoiceNo());
    applyInvoice(inv, r);
    InvoiceEntity saved = invoiceRepository.save(inv);
    applyStock(saved, +1);
    history(saved.getId(), "EDITED", "Total " + money(saved.getTotal()), saved.getCreatedBy());
    return toInvoice(saved);
  }

  @Transactional
  public void deleteInvoice(Long id) {
    InvoiceEntity inv = requireInvoice(id);
    applyStock(inv, -1);
    invoiceRepository.delete(inv);
  }

  private void applyInvoice(InvoiceEntity inv, InvoiceRequest r) {
    if (r.bankAccountId() != null) inv.setBankAccountId(r.bankAccountId());
    if (r.projectId() != null) inv.setProjectId(r.projectId());
    inv.setParty(r.partyId() == null ? null : requireParty(r.partyId()));
    inv.setInvoiceDate(r.invoiceDate() == null ? LocalDate.now().toString() : r.invoiceDate());
    inv.setDueDate(r.dueDate());
    inv.setPaymentType(r.paymentType() == null ? "Cash" : r.paymentType());
    inv.setPaymentReference(r.paymentReference());
    inv.setBillingName(r.billingName());
    inv.setBillingAddress(r.billingAddress());
    inv.setNotes(r.notes());
    inv.setStateOfSupply(r.stateOfSupply());
    inv.setInvoicePrefix(r.invoicePrefix());
    inv.setTerms(r.terms());
    if (r.isCash() != null) inv.setCash(r.isCash());

    inv.getLines().clear();
    BigDecimal sub = BigDecimal.ZERO;   // net of line discounts, before tax
    BigDecimal tax = BigDecimal.ZERO;
    int order = 0;
    if (r.lines() != null) {
      for (InvoiceLineRequest lr : r.lines()) {
        if (lr.itemName() == null || lr.itemName().isBlank()) continue;
        InvoiceLineEntity line = new InvoiceLineEntity();
        line.setItemId(lr.itemId());
        line.setItemName(lr.itemName().trim());
        line.setDescription(lr.description());
        line.setUnit(lr.unit());
        line.setQuantity(nz(lr.quantity()).compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ONE : lr.quantity());
        line.setRate(money(lr.rate()));
        line.setTaxPercent(nz(lr.taxPercent()));

        BigDecimal gross = line.getQuantity().multiply(line.getRate());
        // A percent discount wins if given; otherwise use the flat amount.
        BigDecimal lineDisc =
            nz(lr.discountPercent()).compareTo(BigDecimal.ZERO) > 0
                ? gross.multiply(nz(lr.discountPercent())).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : money(lr.discountAmount());
        if (lineDisc.compareTo(gross) > 0) lineDisc = gross;
        line.setDiscountPercent(nz(lr.discountPercent()));
        line.setDiscountAmount(money(lineDisc));

        BigDecimal taxable = gross.subtract(lineDisc);
        BigDecimal lineTax = taxable.multiply(line.getTaxPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        line.setTaxAmount(money(lineTax));
        line.setAmount(money(taxable.add(lineTax)));
        line.setSortOrder(order++);
        inv.addLine(line);

        sub = sub.add(taxable);
        tax = tax.add(lineTax);
      }
    }
    inv.setSubTotal(money(sub));
    inv.setTaxAmount(money(tax));

    // Whole-document discount: percent of sub-total if given, else the flat amount.
    BigDecimal headerDisc =
        nz(r.discountPercent()).compareTo(BigDecimal.ZERO) > 0
            ? sub.multiply(nz(r.discountPercent())).divide(HUNDRED, 2, RoundingMode.HALF_UP)
            : money(r.discount());
    inv.setDiscountPercent(nz(r.discountPercent()));
    inv.setDiscount(money(headerDisc));
    inv.setRoundOff(money(r.roundOff()));

    BigDecimal total = sub.add(tax).subtract(headerDisc).add(nz(inv.getRoundOff()));
    inv.setTotal(money(total.max(BigDecimal.ZERO)));

    // Cash documents are settled in full unless the caller says otherwise.
    BigDecimal paid =
        r.paidAmount() != null ? r.paidAmount() : (inv.isCash() ? inv.getTotal() : BigDecimal.ZERO);
    inv.setPaidAmount(money(paid.min(inv.getTotal())));
  }

  /** Sales take stock out, purchases put it back. {@code sign} flips for edits/deletes. */
  private void applyStock(InvoiceEntity inv, int sign) {
    int direction =
        switch (inv.getDocType()) {
          case "SALE" -> -1;
          case "PURCHASE" -> +1;
          case "SALE_RETURN" -> +1;
          case "PURCHASE_RETURN" -> -1;
          default -> 0; // estimates/orders don't move stock
        };
    if (direction == 0) return;
    for (InvoiceLineEntity line : inv.getLines()) {
      if (line.getItemId() == null) continue;
      itemRepository
          .findById(line.getItemId())
          .filter(i -> !i.isService())
          .ifPresent(item -> {
            BigDecimal delta = nz(line.getQuantity()).multiply(BigDecimal.valueOf((long) direction * sign));
            item.setStockQty(nz(item.getStockQty()).add(delta));
            itemRepository.save(item);
          });
    }
  }

  private String nextInvoiceNo(String docType) {
    String prefix =
        switch (docType) {
          case "PURCHASE" -> "PUR";
          case "ESTIMATE" -> "EST";
          case "SALE_ORDER" -> "SO";
          case "DELIVERY_CHALLAN" -> "DC";
          case "SALE_RETURN" -> "CN";
          case "PURCHASE_RETURN" -> "DN";
          default -> "INV";
        };
    return prefix + "-" + (1001 + invoiceRepository.countByDocType(docType));
  }

  private InvoiceResponse toInvoice(InvoiceEntity inv) {
    BigDecimal balance = nz(inv.getTotal()).subtract(nz(inv.getPaidAmount()));
    String status = statusOf(inv);
    List<InvoiceLineDto> lines =
        inv.getLines().stream()
            .map(l -> new InvoiceLineDto(
                l.getId(), l.getItemId(), l.getItemName(), l.getDescription(), l.getUnit(),
                nz(l.getQuantity()), money(l.getRate()),
                nz(l.getDiscountPercent()), money(l.getDiscountAmount()),
                nz(l.getTaxPercent()), money(l.getTaxAmount()), money(l.getAmount())))
            .toList();
    return new InvoiceResponse(
        inv.getId(), inv.getDocType(), inv.getInvoiceNo(),
        inv.getParty() == null ? null : inv.getParty().getId(),
        inv.getParty() == null ? null : inv.getParty().getName(),
        inv.getInvoiceDate(), inv.getDueDate(),
        money(inv.getSubTotal()), money(inv.getDiscount()), money(inv.getTaxAmount()),
        money(inv.getTotal()), money(inv.getPaidAmount()), money(balance),
        inv.getPaymentType(), inv.getPaymentReference(), inv.getBillingName(), inv.getBillingAddress(),
        inv.isCash(), inv.getStateOfSupply(), inv.getInvoicePrefix(),
        inv.getTerms(), nz(inv.getDiscountPercent()), money(inv.getRoundOff()),
        status, inv.isCancelled(), inv.getNotes(), inv.getBankAccountId(), inv.getProjectId(), lines);
  }

  // ================= Document actions (Vyapar's row menu) =================

  private static final DateTimeFormatter HISTORY_AT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  private void history(Long invoiceId, String action, String detail, Long userId) {
    InvoiceHistoryEntity h = new InvoiceHistoryEntity();
    h.setInvoiceId(invoiceId);
    h.setAction(action);
    h.setDetail(detail);
    h.setUserId(userId);
    h.setAt(LocalDateTime.now());
    invoiceHistoryRepository.save(h);
  }

  /**
   * Cancel or reopen a document. Cancelling keeps the row and its number but stops it counting
   * towards balances and stock — Vyapar's "Cancel Invoice", which is not a delete.
   */
  @Transactional
  public InvoiceResponse setCancelled(Long id, boolean cancelled, Long userId) {
    InvoiceEntity inv = requireInvoice(id);
    if (inv.isCancelled() == cancelled) return toInvoice(inv);
    // Cancelling reverses the document's stock effect; reopening re-applies it.
    applyStock(inv, cancelled ? -1 : 1);
    inv.setCancelled(cancelled);
    invoiceRepository.save(inv);
    history(id, cancelled ? "CANCELLED" : "REOPENED", inv.getInvoiceNo(), userId);
    return toInvoice(inv);
  }

  /** Copy a document into a fresh, unpaid one with the next number — Vyapar's "Duplicate". */
  @Transactional
  public InvoiceResponse duplicateInvoice(Long id, Long userId) {
    InvoiceEntity src = requireInvoice(id);
    InvoiceEntity copy = new InvoiceEntity();
    copy.setDocType(src.getDocType());
    copy.setInvoiceNo(nextInvoiceNo(src.getDocType()));
    copy.setInvoicePrefix(src.getInvoicePrefix());
    copy.setParty(src.getParty());
    copy.setInvoiceDate(LocalDate.now().toString());
    copy.setDueDate(src.getDueDate());
    copy.setSubTotal(src.getSubTotal());
    copy.setDiscount(src.getDiscount());
    copy.setDiscountPercent(src.getDiscountPercent());
    copy.setTaxAmount(src.getTaxAmount());
    copy.setTotal(src.getTotal());
    copy.setRoundOff(src.getRoundOff());
    // A duplicate starts unpaid — the money on the original was not received twice.
    copy.setPaidAmount(BigDecimal.ZERO);
    copy.setCash(false);
    copy.setPaymentType("Credit");
    copy.setStateOfSupply(src.getStateOfSupply());
    copy.setTerms(src.getTerms());
    copy.setNotes(src.getNotes());
    copy.setBankAccountId(src.getBankAccountId());
    copy.setProjectId(src.getProjectId());
    copy.setCreatedBy(userId);
    for (InvoiceLineEntity l : src.getLines()) {
      copy.addLine(copyLine(l));
    }
    InvoiceEntity saved = invoiceRepository.save(copy);
    applyStock(saved, 1);
    history(saved.getId(), "DUPLICATED", "Copied from " + src.getInvoiceNo(), userId);
    return toInvoice(saved);
  }

  /**
   * Turn a sale into a credit note, or a purchase into a debit note — Vyapar's "Convert To Return".
   * The return mirrors the source document's lines at the same rates.
   */
  @Transactional
  public InvoiceResponse convertToReturn(Long id, Long userId) {
    InvoiceEntity src = requireInvoice(id);
    String returnType =
        switch (src.getDocType()) {
          case "SALE" -> "SALE_RETURN";
          case "PURCHASE" -> "PURCHASE_RETURN";
          default -> throw new IllegalArgumentException(
              "Only a sale or a purchase can be converted to a return.");
        };
    InvoiceEntity ret = new InvoiceEntity();
    ret.setDocType(returnType);
    ret.setInvoiceNo(nextInvoiceNo(returnType));
    ret.setParty(src.getParty());
    ret.setInvoiceDate(LocalDate.now().toString());
    ret.setSubTotal(src.getSubTotal());
    ret.setDiscount(src.getDiscount());
    ret.setDiscountPercent(src.getDiscountPercent());
    ret.setTaxAmount(src.getTaxAmount());
    ret.setTotal(src.getTotal());
    ret.setRoundOff(src.getRoundOff());
    ret.setPaidAmount(BigDecimal.ZERO);
    ret.setCash(false);
    ret.setPaymentType("Credit");
    ret.setStateOfSupply(src.getStateOfSupply());
    ret.setNotes("Return against " + src.getInvoiceNo());
    ret.setBankAccountId(src.getBankAccountId());
    ret.setProjectId(src.getProjectId());
    ret.setCreatedBy(userId);
    for (InvoiceLineEntity l : src.getLines()) {
      ret.addLine(copyLine(l));
    }
    InvoiceEntity saved = invoiceRepository.save(ret);
    applyStock(saved, 1);
    history(src.getId(), "CONVERTED", "Return " + saved.getInvoiceNo() + " raised", userId);
    history(saved.getId(), "CREATED", "Converted from " + src.getInvoiceNo(), userId);
    return toInvoice(saved);
  }

  private static InvoiceLineEntity copyLine(InvoiceLineEntity l) {
    InvoiceLineEntity c = new InvoiceLineEntity();
    c.setItemId(l.getItemId());
    c.setItemName(l.getItemName());
    c.setDescription(l.getDescription());
    c.setUnit(l.getUnit());
    c.setQuantity(l.getQuantity());
    c.setRate(l.getRate());
    c.setDiscountPercent(l.getDiscountPercent());
    c.setDiscountAmount(l.getDiscountAmount());
    c.setTaxPercent(l.getTaxPercent());
    c.setTaxAmount(l.getTaxAmount());
    c.setAmount(l.getAmount());
    c.setSortOrder(l.getSortOrder());
    return c;
  }

  @Transactional(readOnly = true)
  public List<InvoiceHistoryRow> invoiceHistory(Long invoiceId) {
    return invoiceHistoryRepository.findByInvoiceIdOrderByIdDesc(invoiceId).stream()
        .map(h -> new InvoiceHistoryRow(
            h.getId(),
            h.getAction(),
            h.getDetail(),
            h.getUserId(),
            h.getAt() == null ? null : h.getAt().format(HISTORY_AT)))
        .toList();
  }

  // ================= Link Payment to Txns =================

  /**
   * The party's documents that still have something outstanding, for the Link Payment dialog.
   *
   * @param paymentId when editing an existing payment, its own links are shown as already applied
   *     rather than counted against the remaining balance
   */
  @Transactional(readOnly = true)
  public List<OpenTxnRow> openTransactions(Long partyId, Long paymentId) {
    List<InvoiceEntity> invoices =
        invoiceRepository.findByParty_IdAndDocTypeInAndCancelledFalseOrderByIdDesc(partyId, POSTED);
    if (invoices.isEmpty()) return List.of();

    List<Long> invoiceIds = invoices.stream().map(InvoiceEntity::getId).toList();
    // One query for every link on this page — not one per document.
    Map<Long, BigDecimal> mine = new LinkedHashMap<>();
    for (PaymentLinkEntity link : paymentLinkRepository.findByInvoiceIdIn(invoiceIds)) {
      if (paymentId != null && paymentId.equals(link.getPaymentId())) {
        mine.merge(link.getInvoiceId(), nz(link.getAmount()), BigDecimal::add);
      }
    }

    List<OpenTxnRow> rows = new ArrayList<>();
    for (InvoiceEntity inv : invoices) {
      BigDecimal linkedHere = money(mine.getOrDefault(inv.getId(), BigDecimal.ZERO));
      BigDecimal balance = money(nz(inv.getTotal()).subtract(nz(inv.getPaidAmount())));
      // Nothing left to settle and nothing of ours on it — not worth offering.
      if (balance.compareTo(BigDecimal.ZERO) <= 0 && linkedHere.compareTo(BigDecimal.ZERO) <= 0) continue;
      rows.add(new OpenTxnRow(
          inv.getId(),
          inv.getDocType(),
          docLabel(inv.getDocType()),
          inv.getInvoiceNo(),
          inv.getInvoiceDate(),
          money(inv.getTotal()),
          balance.add(linkedHere),
          linkedHere));
    }
    return rows;
  }

  /**
   * Replace a payment's links and push the resulting paid amounts onto the documents.
   *
   * <p>Runs in three bulk steps — delete this payment's links, insert the new ones, then recompute
   * each touched document's paid amount from all of its links — so the cost is proportional to the
   * documents actually involved rather than to the ledger.
   */
  private void applyLinks(PaymentEntity payment, List<PaymentLinkDto> links) {
    List<PaymentLinkEntity> existing = paymentLinkRepository.findByPaymentId(payment.getId());
    List<Long> touched = new ArrayList<>(existing.stream().map(PaymentLinkEntity::getInvoiceId).toList());

    paymentLinkRepository.deleteAll(existing);
    paymentLinkRepository.flush();

    if (links != null) {
      for (PaymentLinkDto l : links) {
        if (l.invoiceId() == null || nz(l.amount()).compareTo(BigDecimal.ZERO) <= 0) continue;
        PaymentLinkEntity e = new PaymentLinkEntity();
        e.setPaymentId(payment.getId());
        e.setInvoiceId(l.invoiceId());
        e.setAmount(money(l.amount()));
        paymentLinkRepository.save(e);
        touched.add(l.invoiceId());
      }
    }

    recomputePaidAmounts(touched);
  }

  /** Set paid_amount on each given document to the sum of every link pointing at it. */
  private void recomputePaidAmounts(List<Long> invoiceIds) {
    List<Long> distinct = invoiceIds.stream().distinct().toList();
    if (distinct.isEmpty()) return;

    Map<Long, BigDecimal> paidByInvoice = new LinkedHashMap<>();
    for (PaymentLinkEntity link : paymentLinkRepository.findByInvoiceIdIn(distinct)) {
      paidByInvoice.merge(link.getInvoiceId(), nz(link.getAmount()), BigDecimal::add);
    }
    for (InvoiceEntity inv : invoiceRepository.findAllById(distinct)) {
      BigDecimal paid = money(paidByInvoice.getOrDefault(inv.getId(), BigDecimal.ZERO));
      // Never mark a document as more than fully paid.
      inv.setPaidAmount(paid.min(money(inv.getTotal())));
      invoiceRepository.save(inv);
    }
  }

  // ================= Settings =================

  @Transactional
  public SettingsDto getSettings() {
    return toSettings(settingsRow());
  }

  @Transactional
  public SettingsDto updateSettings(SettingsDto r) {
    VyaparSettingsEntity s = settingsRow();
    // Decimal places are what every rendered amount depends on; keep them sane.
    s.setAmountDecimals(Math.max(0, Math.min(3, r.amountDecimals())));
    s.setQuantityDecimals(Math.max(0, Math.min(3, r.quantityDecimals())));
    s.setRoundOffEnabled(r.roundOffEnabled());
    s.setRoundOffMode(r.roundOffMode() == null ? "NEAREST" : r.roundOffMode());
    s.setRoundOffTo(r.roundOffTo() <= 0 ? 1 : r.roundOffTo());
    s.setDueDatesEnabled(r.dueDatesEnabled());
    s.setLinkPaymentsEnabled(r.linkPaymentsEnabled());
    s.setItemWiseTax(r.itemWiseTax());
    s.setItemWiseDiscount(r.itemWiseDiscount());
    s.setDisplayPurchasePrice(r.displayPurchasePrice());
    s.setTransactionWiseTax(r.transactionWiseTax());
    s.setTransactionWiseDiscount(r.transactionWiseDiscount());
    s.setEstimateEnabled(r.estimateEnabled());
    s.setProformaEnabled(r.proformaEnabled());
    s.setOrdersEnabled(r.ordersEnabled());
    s.setDeliveryChallanEnabled(r.deliveryChallanEnabled());
    s.setPrefixes(r.prefixes());
    return toSettings(settingsRepository.save(s));
  }

  /** The single settings row, created on first use if the migration's seed is missing. */
  private VyaparSettingsEntity settingsRow() {
    return settingsRepository.findById(1L)
        .orElseGet(() -> settingsRepository.save(new VyaparSettingsEntity()));
  }

  private static SettingsDto toSettings(VyaparSettingsEntity s) {
    return new SettingsDto(
        s.getAmountDecimals(), s.getQuantityDecimals(),
        s.isRoundOffEnabled(), s.getRoundOffMode(), s.getRoundOffTo(),
        s.isDueDatesEnabled(), s.isLinkPaymentsEnabled(),
        s.isItemWiseTax(), s.isItemWiseDiscount(), s.isDisplayPurchasePrice(),
        s.isTransactionWiseTax(), s.isTransactionWiseDiscount(),
        s.isEstimateEnabled(), s.isProformaEnabled(), s.isOrdersEnabled(),
        s.isDeliveryChallanEnabled(), s.getPrefixes());
  }

  // ================= Payments =================

  @Transactional(readOnly = true)
  public List<PaymentResponse> getPayments(String direction, Long projectId) {
    List<PaymentEntity> list =
        (direction == null || direction.isBlank())
            ? paymentRepository.findAllByOrderByIdDesc()
            : paymentRepository.findAllByDirectionOrderByIdDesc(direction.toUpperCase());
    List<PaymentEntity> scoped =
        list.stream().filter(p -> inScope(p.getProjectId(), projectId)).toList();
    // One links query for the whole page rather than one per payment.
    Map<Long, BigDecimal> linked = linkedAmountsByPayment(scoped.stream().map(PaymentEntity::getId).toList());
    return scoped.stream().map(p -> toPayment(p, linked, null)).toList();
  }

  @Transactional
  public PaymentResponse createPayment(PaymentRequest r) {
    PaymentEntity p = new PaymentEntity();
    p.setBankAccountId(r.bankAccountId());
    p.setProjectId(r.projectId());
    p.setDirection(r.direction() == null ? "IN" : r.direction().toUpperCase());
    p.setParty(r.partyId() == null ? null : requireParty(r.partyId()));
    p.setInvoiceId(r.invoiceId());
    p.setPaymentDate(r.paymentDate() == null ? LocalDate.now().toString() : r.paymentDate());
    p.setAmount(money(r.amount()));
    if (r.mode() != null) p.setMode(r.mode());
    p.setReference(r.reference());
    p.setNotes(r.notes());
    PaymentEntity saved = paymentRepository.save(p);

    // Vyapar spreads one receipt across many documents. `links` is the full picture; a bare
    // `invoiceId` is the old single-invoice shape and is treated as a one-line link so existing
    // callers (the "Record Payment" action on an invoice row) keep working.
    List<PaymentLinkDto> links = r.links();
    if ((links == null || links.isEmpty()) && r.invoiceId() != null) {
      links = List.of(new PaymentLinkDto(r.invoiceId(), null, null, money(r.amount())));
    }
    applyLinks(saved, links);

    for (PaymentLinkDto l : links == null ? List.<PaymentLinkDto>of() : links) {
      if (l.invoiceId() != null) {
        history(l.invoiceId(), "PAYMENT", "Linked " + money(l.amount()), null);
      }
    }
    return toPayment(saved, null, links);
  }

  /** Re-link an existing payment — the Link Payment dialog reopened on a saved receipt. */
  @Transactional
  public PaymentResponse relinkPayment(Long id, List<PaymentLinkDto> links) {
    PaymentEntity p =
        paymentRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));
    applyLinks(p, links);
    return toPayment(p, null, links);
  }

  @Transactional
  public void deletePayment(Long id) {
    PaymentEntity p =
        paymentRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));
    // Drop the links first, then rebuild the paid amounts of the documents they pointed at, so
    // deleting a receipt puts those bills back to outstanding.
    List<Long> touched = paymentLinkRepository.findByPaymentId(id).stream()
        .map(PaymentLinkEntity::getInvoiceId)
        .toList();
    paymentLinkRepository.deleteByPaymentId(id);
    paymentLinkRepository.flush();
    paymentRepository.delete(p);
    recomputePaidAmounts(touched);
  }

  /**
   * @param linked pre-computed link totals when rendering a list; null makes this fetch its own
   * @param known the links just written, to avoid a re-read straight after a save
   */
  private PaymentResponse toPayment(
      PaymentEntity p, Map<Long, BigDecimal> linked, List<PaymentLinkDto> known) {
    BigDecimal amount = money(p.getAmount());
    BigDecimal linkedAmount;
    if (linked != null) {
      linkedAmount = money(linked.getOrDefault(p.getId(), BigDecimal.ZERO));
    } else if (known != null) {
      linkedAmount = money(known.stream().map(l -> nz(l.amount())).reduce(BigDecimal.ZERO, BigDecimal::add));
    } else {
      linkedAmount = money(paymentLinkRepository.findByPaymentId(p.getId()).stream()
          .map(l -> nz(l.getAmount()))
          .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    return new PaymentResponse(
        p.getId(), p.getDirection(),
        p.getParty() == null ? null : p.getParty().getId(),
        p.getParty() == null ? null : p.getParty().getName(),
        p.getInvoiceId(), p.getPaymentDate(), amount, p.getMode(), p.getReference(), p.getNotes(),
        p.getBankAccountId(), p.getProjectId(),
        linkedAmount, money(amount.subtract(linkedAmount).max(BigDecimal.ZERO)),
        known == null ? List.of() : known);
  }

  /** A saved payment's links, for reopening the Link Payment dialog. */
  @Transactional(readOnly = true)
  public List<PaymentLinkDto> paymentLinks(Long paymentId) {
    List<PaymentLinkEntity> links = paymentLinkRepository.findByPaymentId(paymentId);
    if (links.isEmpty()) return List.of();
    // Resolve every referenced document in one query so the dialog can label the rows.
    Map<Long, InvoiceEntity> byId = new LinkedHashMap<>();
    for (InvoiceEntity inv :
        invoiceRepository.findAllById(links.stream().map(PaymentLinkEntity::getInvoiceId).distinct().toList())) {
      byId.put(inv.getId(), inv);
    }
    return links.stream()
        .map(l -> {
          InvoiceEntity inv = byId.get(l.getInvoiceId());
          return new PaymentLinkDto(
              l.getInvoiceId(),
              inv == null ? null : inv.getInvoiceNo(),
              inv == null ? null : inv.getDocType(),
              money(l.getAmount()));
        })
        .toList();
  }

  // ================= Dashboard =================

  @Transactional(readOnly = true)
  public DashboardSummary getDashboard(Long projectId) {
    Map<Long, BigDecimal> balances = balancesByParty(projectId);
    BigDecimal receivable = BigDecimal.ZERO;
    BigDecimal payable = BigDecimal.ZERO;
    long recvParties = 0;
    long payParties = 0;

    // Parties are global; their receivable/payable position follows the project-scoped balances.
    for (PartyEntity p : partyRepository.findAll()) {
      BigDecimal bal = nz(p.getOpeningBalance()).add(balances.getOrDefault(p.getId(), BigDecimal.ZERO));
      if (bal.compareTo(BigDecimal.ZERO) > 0) {
        receivable = receivable.add(bal);
        recvParties++;
      } else if (bal.compareTo(BigDecimal.ZERO) < 0) {
        payable = payable.add(bal.abs());
        payParties++;
      }
    }

    BigDecimal totalSale = BigDecimal.ZERO;
    BigDecimal totalPurchase = BigDecimal.ZERO;
    // Sales for the current month, bucketed by day for the trend chart.
    LocalDate now = LocalDate.now();
    String monthPrefix = String.format("%d-%02d", now.getYear(), now.getMonthValue());
    Map<String, BigDecimal> daily = new LinkedHashMap<>();
    int daysInMonth = now.lengthOfMonth();
    for (int d = 1; d <= daysInMonth; d++) daily.put(String.format("%s-%02d", monthPrefix, d), BigDecimal.ZERO);

    for (InvoiceEntity inv : invoiceRepository.findAll()) {
      if (!inScope(inv.getProjectId(), projectId)) continue;
      if ("SALE".equals(inv.getDocType())) {
        totalSale = totalSale.add(nz(inv.getTotal()));
        String date = inv.getInvoiceDate();
        if (date != null && date.length() >= 10) {
          String key = date.substring(0, 10);
          if (daily.containsKey(key)) daily.merge(key, nz(inv.getTotal()), BigDecimal::add);
        }
      } else if ("PURCHASE".equals(inv.getDocType())) {
        totalPurchase = totalPurchase.add(nz(inv.getTotal()));
      }
    }

    BigDecimal cashIn = BigDecimal.ZERO;
    for (PaymentEntity p : paymentRepository.findAll()) {
      if (!inScope(p.getProjectId(), projectId)) continue;
      cashIn = "IN".equals(p.getDirection()) ? cashIn.add(nz(p.getAmount())) : cashIn.subtract(nz(p.getAmount()));
    }

    List<ItemResponse> items = getItems(projectId);
    BigDecimal stockValue = items.stream().map(ItemResponse::stockValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    long lowStock = items.stream().filter(ItemResponse::lowStock).count();

    List<DashboardPoint> trend = new ArrayList<>();
    daily.forEach((k, v) -> trend.add(new DashboardPoint(k, money(v))));

    return new DashboardSummary(
        money(receivable), recvParties, money(payable), payParties,
        money(totalSale), money(totalPurchase), money(cashIn),
        items.size(), money(stockValue), lowStock, trend);
  }

  // ================= helpers =================

  /** A null filter means "all projects"; otherwise the record must belong to that project. */
  private static boolean inScope(Long recordProjectId, Long filter) {
    return filter == null || filter.equals(recordProjectId);
  }

  private PartyEntity requireParty(Long id) {
    return partyRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Party not found: " + id));
  }

  private ItemEntity requireItem(Long id) {
    return itemRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Item not found: " + id));
  }

  private InvoiceEntity requireInvoice(Long id) {
    return invoiceRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + id));
  }
}
