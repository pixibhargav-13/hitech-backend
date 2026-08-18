package com.hitech.erp.procurement.service;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.procurement.db.QuoteEntity;
import com.hitech.erp.procurement.db.QuoteLineEntity;
import com.hitech.erp.procurement.db.QuoteRepository;
import com.hitech.erp.procurement.db.RfqEntity;
import com.hitech.erp.procurement.db.RfqLineEntity;
import com.hitech.erp.procurement.db.RfqRepository;
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

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  // ================= Read =================

  @Transactional(readOnly = true)
  public List<RfqResponse> getRfqs(Long projectId) {
    List<RfqEntity> rows =
        projectId == null ? rfqRepository.findAllByOrderByIdDesc() : rfqRepository.findByProjectIdOrderByIdDesc(projectId);
    Map<Long, String> names = vendorNames(rows);
    return rows.stream().map(r -> toResponse(r, names)).toList();
  }

  @Transactional(readOnly = true)
  public RfqResponse getRfq(Long id) {
    RfqEntity r = require(id);
    return toResponse(r, vendorNames(List.of(r)));
  }

  /**
   * Every vendor and awarded-vendor name in one query, keyed by party id. The comparison screen
   * shows a dozen columns at once; resolving each on its own would be a dozen round trips.
   */
  private Map<Long, String> vendorNames(List<RfqEntity> rfqs) {
    Set<Long> ids =
        rfqs.stream()
            .flatMap(
                r ->
                    java.util.stream.Stream.concat(
                        r.getQuotes().stream().map(QuoteEntity::getVendorPartyId),
                        r.getLines().stream().map(RfqLineEntity::getAwardedVendorPartyId)))
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    if (ids.isEmpty()) return new HashMap<>();
    Map<Long, String> out = new HashMap<>();
    for (PartyEntity p : partyRepository.findAllById(ids)) out.put(p.getId(), p.getName());
    return out;
  }

  // ================= Write =================

  @Transactional
  public RfqResponse create(RfqRequest r, Long userId) {
    RfqEntity e = new RfqEntity();
    e.setRfqNo(nextNumber());
    e.setCreatedBy(userId);
    e.setRfqDate(r.rfqDate() != null ? r.rfqDate() : LocalDate.now().toString());
    apply(e, r);
    return toResponse(rfqRepository.save(e), Map.of());
  }

  @Transactional
  public RfqResponse update(Long id, RfqRequest r) {
    RfqEntity e = require(id);
    if (r.rfqDate() != null) e.setRfqDate(r.rfqDate());
    apply(e, r);
    return toResponse(rfqRepository.save(e), vendorNames(List.of(e)));
  }

  private void apply(RfqEntity e, RfqRequest r) {
    e.setTitle(r.title().trim());
    e.setProjectId(r.projectId());
    e.setDueBy(r.dueBy());
    e.setNotes(r.notes());

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
    return toResponse(rfqRepository.save(e), vendorNames(List.of(e)));
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
    return toResponse(rfqRepository.save(e), vendorNames(List.of(e)));
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
    return toResponse(rfqRepository.save(e), vendorNames(List.of(e)));
  }

  // ================= Helpers =================

  private RfqEntity require(Long id) {
    return rfqRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("RFQ " + id + " not found"));
  }

  private String nextNumber() {
    String prefix = "RFQ-" + LocalDate.now().getYear() + "-";
    return prefix + String.format("%03d", rfqRepository.countByRfqNoStartingWith(prefix) + 1);
  }

  private RfqResponse toResponse(RfqEntity e, Map<Long, String> names) {
    List<RfqLineResponse> lines =
        e.getLines().stream()
            .map(
                l ->
                    new RfqLineResponse(
                        l.getId(),
                        l.getItemId(),
                        l.getItemName(),
                        l.getUnit(),
                        l.getQuantity(),
                        l.getBudgetRate(),
                        l.getAwardedVendorPartyId(),
                        // Guarded: an undecided line has no vendor, and Map.of() throws on a null key.
                        l.getAwardedVendorPartyId() == null ? null : names.get(l.getAwardedVendorPartyId()),
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
                        names.getOrDefault(q.getVendorPartyId(), "Vendor " + q.getVendorPartyId()),
                        q.getVersion(),
                        q.getReceivedOn(),
                        q.getDeliveryDays(),
                        q.getDiscount(),
                        q.getCharges(),
                        q.getTaxPercent(),
                        q.getNote(),
                        q.getLines().stream()
                            .map(l -> new QuoteLineResponse(l.getRfqLineId(), l.getRate(), l.getQuantity(), l.getNote()))
                            .toList()))
            .toList();

    return new RfqResponse(
        e.getId(), e.getRfqNo(), e.getTitle(), e.getProjectId(), e.getStatus(),
        e.getRfqDate(), e.getDueBy(), e.getNotes(), lines, quotes);
  }
}
