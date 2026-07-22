package com.hitech.erp.vyapar.service;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.vyapar.db.InvoiceEntity;
import com.hitech.erp.vyapar.db.InvoiceLineEntity;
import com.hitech.erp.vyapar.db.ItemEntity;
import com.hitech.erp.vyapar.db.PartyEntity;
import com.hitech.erp.vyapar.db.PaymentEntity;
import com.hitech.erp.vyapar.db.StockAdjustmentEntity;
import com.hitech.erp.vyapar.db.InvoiceRepository;
import com.hitech.erp.vyapar.db.ItemRepository;
import com.hitech.erp.vyapar.db.PartyRepository;
import com.hitech.erp.vyapar.db.PaymentRepository;
import com.hitech.erp.vyapar.db.StockAdjustmentRepository;
import com.hitech.erp.vyapar.dto.VyaparDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
  public List<PartyResponse> getParties(String type, Long bankAccountId) {
    List<PartyEntity> parties =
        (type == null || type.isBlank())
            ? partyRepository.findAllByOrderByNameAsc()
            : partyRepository.findAllByPartyTypeOrderByNameAsc(type.toUpperCase());
    Map<Long, BigDecimal> balances = balancesByParty(bankAccountId);
    return parties.stream()
        .filter(p -> inScope(p.getBankAccountId(), bankAccountId))
        .map(p -> toParty(p, balances))
        .toList();
  }

  @Transactional(readOnly = true)
  public PartyResponse getParty(Long id) {
    return toParty(requireParty(id), balancesByParty(null));
  }

  @Transactional
  public PartyResponse createParty(PartyRequest r) {
    PartyEntity p = new PartyEntity();
    applyParty(p, r);
    return toParty(partyRepository.save(p), balancesByParty(null));
  }

  @Transactional
  public PartyResponse updateParty(Long id, PartyRequest r) {
    PartyEntity p = requireParty(id);
    applyParty(p, r);
    return toParty(partyRepository.save(p), balancesByParty(null));
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

    for (InvoiceEntity inv : invoiceRepository.findAllByOrderByIdDesc()) {
      if (inv.getParty() == null || !inv.getParty().getId().equals(partyId)) continue;
      BigDecimal balance = money(nz(inv.getTotal()).subtract(nz(inv.getPaidAmount())));
      rows.add(new PartyLedgerRow(
          inv.getId(),
          "INVOICE",
          docLabel(inv.getDocType()),
          inv.getInvoiceNo(),
          inv.getInvoiceDate(),
          money(inv.getTotal()),
          balance,
          balance.compareTo(BigDecimal.ZERO) <= 0 ? "Paid" : "Unpaid"));
    }

    for (PaymentEntity pay : paymentRepository.findAllByOrderByIdDesc()) {
      if (pay.getParty() == null || !pay.getParty().getId().equals(partyId)) continue;
      rows.add(new PartyLedgerRow(
          pay.getId(),
          "PAYMENT",
          "IN".equals(pay.getDirection()) ? "Payment-In" : "Payment-Out",
          pay.getReference(),
          pay.getPaymentDate(),
          money(pay.getAmount()),
          BigDecimal.ZERO,
          pay.getMode()));
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
  private Map<Long, BigDecimal> balancesByParty(Long bankAccountId) {
    Map<Long, BigDecimal> out = new LinkedHashMap<>();
    for (InvoiceEntity inv : invoiceRepository.findAll()) {
      if (inv.getParty() == null || !POSTED.contains(inv.getDocType())) continue;
      if (!inScope(inv.getBankAccountId(), bankAccountId)) continue;
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
    for (PaymentEntity pay : paymentRepository.findAll()) {
      if (pay.getParty() == null) continue;
      if (!inScope(pay.getBankAccountId(), bankAccountId)) continue;
      // Money in reduces a receivable; money out reduces a payable.
      BigDecimal signed = "IN".equals(pay.getDirection()) ? nz(pay.getAmount()).negate() : nz(pay.getAmount());
      out.merge(pay.getParty().getId(), signed, BigDecimal::add);
    }
    return out;
  }

  // ================= Items =================

  @Transactional(readOnly = true)
  public List<ItemResponse> getItems(Long bankAccountId) {
    return itemRepository.findAllByOrderByNameAsc().stream()
        .filter(i -> inScope(i.getBankAccountId(), bankAccountId))
        .map(this::toItem)
        .toList();
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

    for (InvoiceEntity inv : invoiceRepository.findAllByOrderByIdDesc()) {
      for (InvoiceLineEntity line : inv.getLines()) {
        if (!itemId.equals(line.getItemId())) continue;
        rows.add(new ItemLedgerRow(
            inv.getId(),
            docLabel(inv.getDocType()),
            inv.getInvoiceNo(),
            inv.getParty() == null ? null : inv.getParty().getName(),
            inv.getInvoiceDate(),
            nz(line.getQuantity()),
            money(line.getRate()),
            statusOf(inv)));
      }
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
    if (r.isActive() != null) i.setActive(r.isActive());
  }

  private ItemResponse toItem(ItemEntity i) {
    BigDecimal value = nz(i.getStockQty()).multiply(nz(i.getPurchasePrice()));
    boolean low =
        !i.isService()
            && nz(i.getLowStockAlert()).compareTo(BigDecimal.ZERO) > 0
            && nz(i.getStockQty()).compareTo(nz(i.getLowStockAlert())) <= 0;
    return new ItemResponse(
        i.getId(), i.getName(), i.getCategory(), i.getDescription(), i.getItemCode(), i.getHsn(), i.getUnit(),
        money(i.getSalePrice()), money(i.getPurchasePrice()), nz(i.getTaxPercent()),
        nz(i.getStockQty()), nz(i.getLowStockAlert()), i.isService(), i.isActive(),
        i.getBankAccountId(), money(value), low);
  }

  // ================= Invoices =================

  @Transactional(readOnly = true)
  public List<InvoiceResponse> getInvoices(String docType, Long bankAccountId) {
    List<InvoiceEntity> list =
        (docType == null || docType.isBlank())
            ? invoiceRepository.findAllByOrderByIdDesc()
            : invoiceRepository.findAllByDocTypeOrderByIdDesc(docType.toUpperCase());
    return list.stream()
        .filter(inv -> inScope(inv.getBankAccountId(), bankAccountId))
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
    inv.setParty(r.partyId() == null ? null : requireParty(r.partyId()));
    inv.setInvoiceDate(r.invoiceDate() == null ? LocalDate.now().toString() : r.invoiceDate());
    inv.setDueDate(r.dueDate());
    inv.setPaymentType(r.paymentType() == null ? "Cash" : r.paymentType());
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
    String status =
        balance.compareTo(BigDecimal.ZERO) <= 0
            ? "Paid"
            : nz(inv.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0 ? "Partial" : "Unpaid";
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
        inv.getPaymentType(), inv.isCash(), inv.getStateOfSupply(), inv.getInvoicePrefix(),
        inv.getTerms(), nz(inv.getDiscountPercent()), money(inv.getRoundOff()),
        status, inv.getNotes(), inv.getBankAccountId(), lines);
  }

  // ================= Payments =================

  @Transactional(readOnly = true)
  public List<PaymentResponse> getPayments(String direction, Long bankAccountId) {
    List<PaymentEntity> list =
        (direction == null || direction.isBlank())
            ? paymentRepository.findAllByOrderByIdDesc()
            : paymentRepository.findAllByDirectionOrderByIdDesc(direction.toUpperCase());
    return list.stream()
        .filter(p -> inScope(p.getBankAccountId(), bankAccountId))
        .map(this::toPayment)
        .toList();
  }

  @Transactional
  public PaymentResponse createPayment(PaymentRequest r) {
    PaymentEntity p = new PaymentEntity();
    p.setBankAccountId(r.bankAccountId());
    p.setDirection(r.direction() == null ? "IN" : r.direction().toUpperCase());
    p.setParty(r.partyId() == null ? null : requireParty(r.partyId()));
    p.setInvoiceId(r.invoiceId());
    p.setPaymentDate(r.paymentDate() == null ? LocalDate.now().toString() : r.paymentDate());
    p.setAmount(money(r.amount()));
    if (r.mode() != null) p.setMode(r.mode());
    p.setReference(r.reference());
    p.setNotes(r.notes());
    PaymentEntity saved = paymentRepository.save(p);

    // Settling a specific invoice also moves that invoice's paid amount.
    if (r.invoiceId() != null) {
      invoiceRepository
          .findById(r.invoiceId())
          .ifPresent(inv -> {
            BigDecimal paid = nz(inv.getPaidAmount()).add(money(r.amount()));
            inv.setPaidAmount(money(paid.min(nz(inv.getTotal()))));
            invoiceRepository.save(inv);
          });
    }
    return toPayment(saved);
  }

  @Transactional
  public void deletePayment(Long id) {
    paymentRepository.delete(
        paymentRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id)));
  }

  private PaymentResponse toPayment(PaymentEntity p) {
    return new PaymentResponse(
        p.getId(), p.getDirection(),
        p.getParty() == null ? null : p.getParty().getId(),
        p.getParty() == null ? null : p.getParty().getName(),
        p.getInvoiceId(), p.getPaymentDate(), money(p.getAmount()), p.getMode(), p.getReference(), p.getNotes(), p.getBankAccountId());
  }

  // ================= Dashboard =================

  @Transactional(readOnly = true)
  public DashboardSummary getDashboard(Long bankAccountId) {
    Map<Long, BigDecimal> balances = balancesByParty(bankAccountId);
    BigDecimal receivable = BigDecimal.ZERO;
    BigDecimal payable = BigDecimal.ZERO;
    long recvParties = 0;
    long payParties = 0;

    for (PartyEntity p : partyRepository.findAll()) {
      if (!inScope(p.getBankAccountId(), bankAccountId)) continue;
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
      if (!inScope(inv.getBankAccountId(), bankAccountId)) continue;
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
      if (!inScope(p.getBankAccountId(), bankAccountId)) continue;
      cashIn = "IN".equals(p.getDirection()) ? cashIn.add(nz(p.getAmount())) : cashIn.subtract(nz(p.getAmount()));
    }

    List<ItemResponse> items = getItems(bankAccountId);
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

  /** A null filter means "all bank accounts"; otherwise the record must match. */
  private static boolean inScope(Long recordBankAccountId, Long filter) {
    return filter == null || filter.equals(recordBankAccountId);
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
