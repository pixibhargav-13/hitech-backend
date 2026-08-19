package com.hitech.erp.procurement.service;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.procurement.db.QuoteEntity;
import com.hitech.erp.procurement.db.QuoteLineEntity;
import com.hitech.erp.procurement.db.QuoteRepository;
import com.hitech.erp.procurement.db.RfqEntity;
import com.hitech.erp.procurement.db.RfqLineEntity;
import com.hitech.erp.procurement.db.RfqRepository;
import com.hitech.erp.procurement.db.RfqSupplierEntity;
import com.hitech.erp.procurement.db.RfqSupplierRepository;
import com.hitech.erp.procurement.dto.ProcurementDtos.*;
import com.hitech.erp.vyapar.db.PartyEntity;
import com.hitech.erp.vyapar.db.PartyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requests for quotation, the quotes against them, and the award decision.
 *
 * <p>Two rules run through the whole class:
 *
 * <ul>
 *   <li><b>A quote is priced per line.</b> A vendor routinely quotes four of five lines and skips
 *       the fifth, so a missing price stays null and is reported as "no quote" rather than zero.
 *   <li><b>An award is made per line.</b> A five-line enquiry commonly splits across three
 *       suppliers, so the winner lives on the line, and awarding produces one Vyapar purchase order
 *       per winning vendor rather than one per enquiry.
 * </ul>
 *
 * <p>Status is derived rather than set by hand — it is a function of what has come back and what
 * has been decided, and letting a client post an arbitrary status is how a list ends up showing
 * "Awarded" on an enquiry nobody has decided.
 */
@Service
@RequiredArgsConstructor
public class RfqService {

  private final RfqRepository rfqRepository;
  private final QuoteRepository quoteRepository;
  private final PartyRepository partyRepository;
  private final RfqSupplierRepository supplierRepository;

  private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  // ================= Read =================

  @Transactional(readOnly = true)
  public List<RfqResponse> getRfqs(Long projectId) {
    List<RfqEntity> rows =
        projectId == null ? rfqRepository.findAllByOrderByIdDesc() : rfqRepository.findByProjectIdOrderByIdDesc(projectId);
    Map<Long, PartyEntity> names = vendors(rows);
    return rows.stream().map(r -> toResponse(r, names)).toList();
  }

  @Transactional(readOnly = true)
  public RfqResponse getRfq(Long id) {
    RfqEntity r = require(id);
    return toResponse(r, vendors(List.of(r)));
  }

  /**
   * Every vendor and awarded-vendor name in one query, keyed by party id. The comparison screen
   * shows a dozen columns at once; resolving each on its own would be a dozen round trips.
   */
  private Map<Long, PartyEntity> vendors(List<RfqEntity> rfqs) {
    Set<Long> ids =
        rfqs.stream()
            .flatMap(
                r ->
                    java.util.stream.Stream.of(
                            r.getQuotes().stream().map(QuoteEntity::getVendorPartyId),
                            r.getLines().stream().map(RfqLineEntity::getAwardedVendorPartyId),
                            r.getSuppliers().stream().map(RfqSupplierEntity::getVendorPartyId))
                        .flatMap(x -> x))
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, PartyEntity> out = new HashMap<>();
    if (ids.isEmpty()) return out;
    for (PartyEntity p : partyRepository.findAllById(ids)) out.put(p.getId(), p);
    return out;
  }

  private static String nameOf(Map<Long, PartyEntity> vendors, Long id) {
    if (id == null) return null;
    PartyEntity p = vendors.get(id);
    return p == null ? "Vendor " + id : p.getName();
  }

  // ================= Write =================

  @Transactional
  public RfqResponse create(RfqRequest r, Long userId) {
    RfqEntity e = new RfqEntity();
    // The form lets the number be edited; blank means "give me the next one".
    e.setRfqNo(r.rfqNo() != null && !r.rfqNo().isBlank() ? r.rfqNo().trim() : nextNumber());
    e.setCreatedBy(userId);
    e.setRfqDate(r.rfqDate() != null ? r.rfqDate() : LocalDate.now().toString());
    apply(e, r);
    return toResponse(rfqRepository.save(e), new HashMap<>());
  }

  @Transactional
  public RfqResponse update(Long id, RfqRequest r) {
    RfqEntity e = require(id);
    if (r.rfqDate() != null) e.setRfqDate(r.rfqDate());
    apply(e, r);
    return toResponse(rfqRepository.save(e), vendors(List.of(e)));
  }

  private void apply(RfqEntity e, RfqRequest r) {
    e.setTitle(r.title().trim());
    e.setProjectId(r.projectId());
    e.setNotes(r.notes());
    e.setTerms(r.terms());
    if (r.taxType() != null) e.setTaxType(r.taxType());
    e.setBiddingStartDate(r.biddingStartDate());
    e.setBiddingEndDate(r.biddingEndDate());
    // The reply deadline and the end of the bidding window are the same date under two names; keep
    // them together so older screens reading dueBy do not go blank.
    e.setDueBy(r.biddingEndDate() != null ? r.biddingEndDate() : r.dueBy());
    e.setDeliveryDate(r.deliveryDate());

    e.setBillToName(r.billToName());
    e.setBillToAddress(r.billToAddress());
    e.setBillToGstin(r.billToGstin());
    boolean same = Boolean.TRUE.equals(r.shipSameAsBill());
    e.setShipSameAsBill(same);
    // "Same as Bill To" is stored resolved as well as flagged: the printed enquiry should not have
    // to re-derive an address, and the flag alone would leave the document blank if it were cleared.
    e.setShipToName(same ? r.billToName() : r.shipToName());
    e.setShipToAddress(same ? r.billToAddress() : r.shipToAddress());
    e.setShipToGstin(same ? r.billToGstin() : r.shipToGstin());

    // Lines are replaced wholesale, but quotes reference them by id, so a line that is still
    // present keeps its identity — otherwise every edit of the enquiry would orphan the prices
    // already received against it.
    Map<Long, RfqLineEntity> existing =
        e.getLines().stream().filter(l -> l.getId() != null).collect(Collectors.toMap(RfqLineEntity::getId, l -> l));
    List<RfqLineEntity> keep = new java.util.ArrayList<>();
    int order = 0;
    if (r.lines() != null) {
      for (RfqLineRequest lr : r.lines()) {
        if (lr.itemName() == null || lr.itemName().isBlank()) continue;
        RfqLineEntity line = lr.id() != null ? existing.get(lr.id()) : null;
        if (line == null) {
          line = new RfqLineEntity();
          line.setRfq(e);
        }
        line.setItemId(lr.itemId());
        line.setItemName(lr.itemName().trim());
        line.setSpecification(lr.specification());
        line.setHsnCode(lr.hsnCode());
        line.setDeliveryDate(lr.deliveryDate());
        line.setUnit(lr.unit());
        line.setQuantity(lr.quantity() == null ? BigDecimal.ONE : lr.quantity());
        line.setBudgetRate(lr.budgetRate());
        line.setSortOrder(order++);
        keep.add(line);
      }
    }
    // A line that has been removed from the enquiry takes its prices with it. Clearing them here
    // rather than leaving it to the database keeps the in-memory quotes consistent with what is
    // about to be written — the same double-delete trap as in delete(), one level down.
    Set<Long> keptIds = keep.stream().map(RfqLineEntity::getId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    for (QuoteEntity q : e.getQuotes()) {
      q.getLines().removeIf(ql -> !keptIds.contains(ql.getRfqLineId()));
    }

    e.getLines().clear();
    e.getLines().addAll(keep);

    // Target suppliers: replace the list, but keep the rows for anyone still invited so their
    // sent-at stamp survives an edit of the enquiry.
    if (r.supplierPartyIds() != null) {
      Set<Long> wanted = new java.util.HashSet<>(r.supplierPartyIds());
      // Never drop a supplier who has already quoted — their column would vanish from the
      // comparison while their prices stayed in the database.
      for (QuoteEntity q : e.getQuotes()) wanted.add(q.getVendorPartyId());
      e.getSuppliers().removeIf(s -> !wanted.contains(s.getVendorPartyId()));
      Set<Long> have = e.getSuppliers().stream().map(RfqSupplierEntity::getVendorPartyId).collect(Collectors.toSet());
      for (Long id : wanted) {
        if (have.contains(id)) continue;
        RfqSupplierEntity s = new RfqSupplierEntity();
        s.setVendorPartyId(id);
        e.addSupplier(s);
      }
    }

    e.setStatus(deriveStatus(e, r.status()));
  }

  /**
   * Status follows the facts: nothing sent yet is a Draft, sent with nothing back is Sent, quotes
   * in is Responses In, and every line decided is Awarded. A caller may only push it to Closed —
   * the one state the data cannot imply.
   */
  private String deriveStatus(RfqEntity e, String requested) {
    if ("Closed".equals(requested)) return "Closed";
    if ("Closed".equals(e.getStatus())) return "Closed";
    boolean anyQuotes = !e.getQuotes().isEmpty();
    boolean allAwarded =
        !e.getLines().isEmpty() && e.getLines().stream().allMatch(l -> l.getAwardedVendorPartyId() != null);
    if (allAwarded) return "Awarded";
    if (anyQuotes) return "Responses In";
    if ("Sent".equals(requested) || "Sent".equals(e.getStatus())) return "Sent";
    return "Draft";
  }

  @Transactional
  public void delete(Long id) {
    RfqEntity e = require(id);
    // Quote lines are reachable twice over: the database cascades them from their quote and from
    // their RFQ line, and Hibernate also removes them as orphans. Left alone the two race, and
    // Hibernate throws StaleStateException on rows Postgres has already taken. Dropping the quotes
    // in their own flush first makes the order deterministic.
    e.getQuotes().clear();
    rfqRepository.saveAndFlush(e);
    rfqRepository.delete(e);
  }

  // ---- Quotes ----

  /**
   * Record what a vendor came back with. A second submission from the same vendor replaces the
   * first and bumps the version, so the comparison always shows one column per vendor and can still
   * say which revision it is looking at.
   */
  @Transactional
  public RfqResponse saveQuote(Long rfqId, QuoteRequest r) {
    RfqEntity e = require(rfqId);
    if (r.vendorPartyId() == null) throw new IllegalArgumentException("Pick a vendor for this quote.");

    QuoteEntity q =
        quoteRepository
            .findByRfq_IdAndVendorPartyId(rfqId, r.vendorPartyId())
            .map(
                found -> {
                  found.setVersion(found.getVersion() + 1);
                  return found;
                })
            .orElseGet(
                () -> {
                  QuoteEntity fresh = new QuoteEntity();
                  fresh.setVendorPartyId(r.vendorPartyId());
                  e.addQuote(fresh);
                  return fresh;
                });

    // A vendor can reply without having been on the original list (forwarded enquiry); record them
    // as invited too, so "5 invited, 6 responded" cannot happen.
    if (e.getSuppliers().stream().noneMatch(s -> s.getVendorPartyId().equals(r.vendorPartyId()))) {
      RfqSupplierEntity s = new RfqSupplierEntity();
      s.setVendorPartyId(r.vendorPartyId());
      e.addSupplier(s);
    }

    q.setReceivedOn(r.receivedOn() != null ? r.receivedOn() : LocalDate.now().toString());
    q.setDeliveryDays(r.deliveryDays());
    q.setDiscount(nz(r.discount()));
    q.setCharges(nz(r.charges()));
    q.setTaxPercent(nz(r.taxPercent()));
    q.setNote(r.note());

    Set<Long> validLines = e.getLines().stream().map(RfqLineEntity::getId).collect(Collectors.toSet());

    // Prices are updated in place rather than cleared and re-added. Clearing first looks simpler,
    // but Hibernate orders the inserts before the orphan deletes within one flush, so a revised
    // quote collides with uq_quote_line on (quote_id, rfq_line_id).
    Map<Long, QuoteLineEntity> current =
        q.getLines().stream().collect(Collectors.toMap(QuoteLineEntity::getRfqLineId, l -> l, (a, b) -> a));
    Set<Long> seen = new java.util.HashSet<>();
    if (r.lines() != null) {
      for (QuoteLineRequest lr : r.lines()) {
        // A price against a line that is not on this enquiry is a client bug; drop it rather than
        // storing a row that can never be displayed.
        if (lr.rfqLineId() == null || !validLines.contains(lr.rfqLineId())) continue;
        QuoteLineEntity line = current.get(lr.rfqLineId());
        if (line == null) {
          line = new QuoteLineEntity();
          line.setRfqLineId(lr.rfqLineId());
          q.addLine(line);
        }
        line.setRate(lr.rate()); // null stays null — "no quote", not zero
        line.setQuantity(lr.quantity());
        line.setNote(lr.note());
        seen.add(lr.rfqLineId());
      }
    }
    // Lines the revision no longer mentions are dropped.
    q.getLines().removeIf(l -> !seen.contains(l.getRfqLineId()));

    e.setStatus(deriveStatus(e, null));
    return toResponse(rfqRepository.save(e), vendors(List.of(e)));
  }

  @Transactional
  public RfqResponse deleteQuote(Long rfqId, Long quoteId) {
    RfqEntity e = require(rfqId);
    e.getQuotes().removeIf(q -> q.getId().equals(quoteId));
    // A line awarded to the vendor whose quote just went has nothing behind it any more.
    e.getLines().stream()
        .filter(l -> l.getAwardedVendorPartyId() != null)
        .filter(l -> e.getQuotes().stream().noneMatch(q -> q.getVendorPartyId().equals(l.getAwardedVendorPartyId())))
        .forEach(
            l -> {
              l.setAwardedVendorPartyId(null);
              l.setAwardReason(null);
            });
    e.setStatus(deriveStatus(e, null));
    return toResponse(rfqRepository.save(e), vendors(List.of(e)));
  }

  // ---- Award ----

  /** Award one line to a vendor, or clear it by passing a null vendor id. */
  @Transactional
  public RfqResponse award(Long rfqId, Long lineId, AwardRequest r) {
    RfqEntity e = require(rfqId);
    RfqLineEntity line =
        e.getLines().stream()
            .filter(l -> l.getId().equals(lineId))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Line " + lineId + " is not on this RFQ"));

    if (r.vendorPartyId() != null) {
      boolean quoted = e.getQuotes().stream().anyMatch(q -> q.getVendorPartyId().equals(r.vendorPartyId()));
      if (!quoted) throw new IllegalArgumentException("That vendor has not quoted this enquiry.");
    }
    line.setAwardedVendorPartyId(r.vendorPartyId());
    line.setAwardReason(r.vendorPartyId() == null ? null : r.reason());

    e.setStatus(deriveStatus(e, null));
    return toResponse(rfqRepository.save(e), vendors(List.of(e)));
  }

  // ---- Sending, and the supplier's own quote link ----

  /**
   * Send the enquiry to its suppliers.
   *
   * <p>Sending means minting one quote link per supplier and stamping them sent. Email is not wired
   * yet, so in practice the link is copied out and sent over WhatsApp - which is how these enquiries
   * actually travel anyway. The link is the whole point: without it every price has to be re-typed
   * by the buyer, six suppliers meaning six quotes keyed by hand.
   *
   * <p>Resending is safe. A supplier who already has a token keeps it, so a second send does not
   * invalidate a link that is already sitting in somebody's chat.
   */
  @Transactional
  public RfqResponse send(Long rfqId, SendRequest r) {
    RfqEntity e = require(rfqId);
    Set<Long> only =
        r != null && r.supplierPartyIds() != null && !r.supplierPartyIds().isEmpty()
            ? new java.util.HashSet<>(r.supplierPartyIds())
            : null;

    for (RfqSupplierEntity s : e.getSuppliers()) {
      if (only != null && !only.contains(s.getVendorPartyId())) continue;
      if (s.getShareToken() == null) s.setShareToken(newToken());
      s.setSentAt(java.time.LocalDateTime.now());
    }
    // Sent is a fact about the enquiry, not a request: deriveStatus will keep it, or move it on to
    // "Responses In" the moment a price arrives.
    e.setStatus(deriveStatus(e, "Sent"));
    return toResponse(rfqRepository.save(e), vendors(List.of(e)));
  }

  /** 256 bits, URL-safe. Long enough that guessing one is not a threat worth modelling. */
  private static String newToken() {
    byte[] b = new byte[32];
    RANDOM.nextBytes(b);
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  /**
   * The enquiry as the supplier sees it, resolved from their link.
   *
   * <p>Two things are deliberately absent: the budget rate, which is the number we are trying not
   * to anchor them to, and every other supplier's prices. A leaked link exposes one supplier's own
   * quote, never the comparison.
   */
  @Transactional
  public PublicRfqResponse publicView(String token) {
    RfqSupplierEntity s = requireToken(token);
    RfqEntity e = s.getRfq();
    // Opening is worth recording: "sent but never opened" needs a resend, "opened and silent"
    // needs a phone call, and without this stamp the two look identical.
    s.setOpenedAt(java.time.LocalDateTime.now());
    supplierRepository.save(s);

    QuoteEntity mine =
        e.getQuotes().stream()
            .filter(q -> q.getVendorPartyId().equals(s.getVendorPartyId()))
            .findFirst()
            .orElse(null);
    Map<Long, QuoteLineEntity> priced =
        mine == null
            ? Map.of()
            : mine.getLines().stream()
                .collect(Collectors.toMap(QuoteLineEntity::getRfqLineId, l -> l, (a, b) -> a));

    String closed = closedReason(e, mine);
    PartyEntity vendor = partyRepository.findById(s.getVendorPartyId()).orElse(null);

    List<PublicRfqLine> lines =
        e.getLines().stream()
            .map(
                l -> {
                  QuoteLineEntity ql = priced.get(l.getId());
                  return new PublicRfqLine(
                      l.getId(),
                      l.getItemName(),
                      l.getSpecification(),
                      l.getHsnCode(),
                      l.getUnit(),
                      l.getQuantity(),
                      l.getDeliveryDate(),
                      ql == null ? null : ql.getRate(),
                      ql == null ? null : ql.getNote());
                })
            .toList();

    return new PublicRfqResponse(
        e.getRfqNo(),
        e.getTitle(),
        e.getBillToName(),
        vendor == null ? null : vendor.getName(),
        e.getRfqDate(),
        e.getBiddingEndDate(),
        e.getDeliveryDate(),
        e.getTerms(),
        e.getShipToName(),
        e.getShipToAddress(),
        closed == null,
        closed,
        mine != null,
        mine == null || mine.getSubmittedAt() == null ? null : mine.getSubmittedAt().toString(),
        mine == null ? null : mine.getDeliveryDays(),
        mine == null ? null : mine.getDiscount(),
        mine == null ? null : mine.getCharges(),
        mine == null ? null : mine.getTaxPercent(),
        mine == null ? null : mine.getNote(),
        lines);
  }

  /**
   * Why the form is read-only, or null if it is still open.
   *
   * <p>A submitted quote locks so a supplier cannot revise it quietly after the comparison has been
   * read. The buyer can unlock to invite a revision - which is a decision, made on our side, not
   * something the supplier can take for themselves.
   */
  private String closedReason(RfqEntity e, QuoteEntity mine) {
    if ("Closed".equals(e.getStatus())) return "This enquiry has been closed.";
    if (mine != null && mine.isLocked())
      return "Your quote has been submitted. Contact the buyer if you need to revise it.";
    String end = e.getBiddingEndDate();
    if (end != null && !end.isBlank()) {
      try {
        if (LocalDate.parse(end).isBefore(LocalDate.now())) return "The bidding window closed on " + end + ".";
      } catch (java.time.format.DateTimeParseException ignored) {
        // A malformed date should not lock a supplier out of quoting.
      }
    }
    return null;
  }

  /** The supplier's own submission. Same storage as a keyed-in quote, marked as theirs and locked. */
  @Transactional
  public PublicRfqResponse publicSubmit(String token, PublicQuoteRequest r) {
    RfqSupplierEntity s = requireToken(token);
    RfqEntity e = s.getRfq();

    QuoteEntity existing =
        e.getQuotes().stream()
            .filter(q -> q.getVendorPartyId().equals(s.getVendorPartyId()))
            .findFirst()
            .orElse(null);
    String closed = closedReason(e, existing);
    if (closed != null) throw new IllegalArgumentException(closed);

    saveQuote(
        e.getId(),
        new QuoteRequest(
            s.getVendorPartyId(),
            LocalDate.now().toString(),
            r.deliveryDays(),
            r.discount(),
            r.charges(),
            r.taxPercent(),
            r.note(),
            r.lines()));

    QuoteEntity saved =
        require(e.getId()).getQuotes().stream()
            .filter(q -> q.getVendorPartyId().equals(s.getVendorPartyId()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Quote did not save"));
    saved.setSource("VENDOR");
    saved.setLocked(true);
    saved.setSubmittedAt(java.time.LocalDateTime.now());
    quoteRepository.save(saved);

    return publicView(token);
  }

  /** Let a supplier revise: clears the lock so their link becomes editable again. */
  @Transactional
  public RfqResponse unlockQuote(Long rfqId, Long quoteId) {
    RfqEntity e = require(rfqId);
    e.getQuotes().stream().filter(q -> q.getId().equals(quoteId)).forEach(q -> q.setLocked(false));
    return toResponse(rfqRepository.save(e), vendors(List.of(e)));
  }

  private RfqSupplierEntity requireToken(String token) {
    return supplierRepository
        .findByShareToken(token)
        .orElseThrow(() -> new EntityNotFoundException("This quote link is not valid any more."));
  }

  // ================= Helpers =================

  private RfqEntity require(Long id) {
    return rfqRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("RFQ " + id + " not found"));
  }

  private String nextNumber() {
    String prefix = "RFQ-" + LocalDate.now().getYear() + "-";
    return prefix + String.format("%03d", rfqRepository.countByRfqNoStartingWith(prefix) + 1);
  }

  private RfqResponse toResponse(RfqEntity e, Map<Long, PartyEntity> names) {
    List<RfqLineResponse> lines =
        e.getLines().stream()
            .map(
                l ->
                    new RfqLineResponse(
                        l.getId(),
                        l.getItemId(),
                        l.getItemName(),
                        l.getSpecification(),
                        l.getHsnCode(),
                        l.getDeliveryDate(),
                        l.getUnit(),
                        l.getQuantity(),
                        l.getBudgetRate(),
                        l.getAwardedVendorPartyId(),
                        nameOf(names, l.getAwardedVendorPartyId()),
                        l.getAwardReason(),
                        l.getSortOrder()))
            .toList();

    List<QuoteResponse> quotes =
        e.getQuotes().stream()
            .map(
                q ->
                    new QuoteResponse(
                        q.getId(),
                        q.getVendorPartyId(),
                        nameOf(names, q.getVendorPartyId()),
                        q.getVersion(),
                        q.getReceivedOn(),
                        q.getDeliveryDays(),
                        q.getDiscount(),
                        q.getCharges(),
                        q.getTaxPercent(),
                        q.getNote(),
                        q.getSource(),
                        q.isLocked(),
                        q.getSubmittedAt() == null ? null : q.getSubmittedAt().toString(),
                        q.getLines().stream()
                            .map(l -> new QuoteLineResponse(l.getRfqLineId(), l.getRate(), l.getQuantity(), l.getNote()))
                            .toList()))
            .toList();

    Set<Long> responded = e.getQuotes().stream().map(QuoteEntity::getVendorPartyId).collect(Collectors.toSet());
    List<RfqSupplierResponse> suppliers =
        e.getSuppliers().stream()
            .map(
                s -> {
                  PartyEntity p = names.get(s.getVendorPartyId());
                  return new RfqSupplierResponse(
                      s.getId(),
                      s.getVendorPartyId(),
                      nameOf(names, s.getVendorPartyId()),
                      p == null ? null : p.getPhone(),
                      p == null ? null : p.getEmail(),
                      s.getSentAt() == null ? null : s.getSentAt().toString(),
                      responded.contains(s.getVendorPartyId()),
                      s.getShareToken(),
                      s.getOpenedAt() == null ? null : s.getOpenedAt().toString());
                })
            .toList();

    return new RfqResponse(
        e.getId(), e.getRfqNo(), e.getTitle(), e.getProjectId(), e.getStatus(),
        e.getRfqDate(), e.getDueBy(),
        e.getTaxType(), e.getBiddingStartDate(), e.getBiddingEndDate(), e.getDeliveryDate(), e.getTerms(),
        e.getBillToName(), e.getBillToAddress(), e.getBillToGstin(),
        e.getShipToName(), e.getShipToAddress(), e.getShipToGstin(), e.isShipSameAsBill(),
        e.getNotes(), lines, suppliers, quotes);
  }
}
