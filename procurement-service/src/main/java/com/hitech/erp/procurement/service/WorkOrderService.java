package com.hitech.erp.procurement.service;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.procurement.db.SubconBillEntity;
import com.hitech.erp.procurement.db.SubconMaterialEntity;
import com.hitech.erp.procurement.db.WorkOrderEntity;
import com.hitech.erp.procurement.db.WorkOrderItemEntity;
import com.hitech.erp.procurement.db.WorkOrderRepository;
import com.hitech.erp.procurement.dto.WorkOrderDtos.*;
import com.hitech.erp.vyapar.db.PartyEntity;
import com.hitech.erp.vyapar.db.PartyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subcontracts: the order, what has been billed against it, and what material went out to him.
 *
 * <p>Three rules run through the class:
 *
 * <ul>
 *   <li><b>Measurement beats a typed quantity.</b> When N x L x W x H are given, the quantity is
 *       their product and a quantity in the request is ignored. Otherwise the two could disagree on
 *       the same row and there would be no way to tell which one the order was signed on.
 *   <li><b>Progress is weighted by value.</b> A line 90% done worth ₹4,40,000 counts for more than
 *       a line 100% done worth ₹3,600. Averaging the percentages would say the order is half
 *       finished when nearly all the money is still in the ground.
 *   <li><b>Billed is a fact, not a plan.</b> Outstanding is the order value less what has actually
 *       been billed, so a subcontractor who has billed past his order shows negative rather than
 *       being quietly clamped at zero.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WorkOrderService {

  private final WorkOrderRepository repository;
  private final PartyRepository partyRepository;

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static boolean positive(BigDecimal v) {
    return v != null && v.signum() > 0;
  }

  // ================= Read =================

  @Transactional(readOnly = true)
  public List<WorkOrderResponse> getAll(Long projectId) {
    List<WorkOrderEntity> rows =
        projectId == null ? repository.findAllByOrderByIdDesc() : repository.findByProjectIdOrderByIdDesc(projectId);
    Map<Long, PartyEntity> vendors = vendors(rows);
    return rows.stream().map(w -> toResponse(w, vendors)).toList();
  }

  @Transactional(readOnly = true)
  public WorkOrderResponse get(Long id) {
    WorkOrderEntity w = require(id);
    return toResponse(w, vendors(List.of(w)));
  }

  /** Every subcontractor name in one query — the list shows twenty at a time. */
  private Map<Long, PartyEntity> vendors(List<WorkOrderEntity> rows) {
    Set<Long> ids =
        rows.stream().map(WorkOrderEntity::getVendorPartyId).filter(Objects::nonNull).collect(Collectors.toSet());
    Map<Long, PartyEntity> out = new HashMap<>();
    if (ids.isEmpty()) return out;
    for (PartyEntity p : partyRepository.findAllById(ids)) out.put(p.getId(), p);
    return out;
  }

  // ================= Write =================

  @Transactional
  public WorkOrderResponse create(WorkOrderRequest r, Long userId) {
    WorkOrderEntity w = new WorkOrderEntity();
    w.setWoNo(r.woNo() != null && !r.woNo().isBlank() ? r.woNo().trim() : nextNumber());
    w.setCreatedBy(userId);
    w.setWoDate(r.woDate() != null ? r.woDate() : LocalDate.now().toString());
    apply(w, r);
    return toResponse(repository.save(w), vendors(List.of(w)));
  }

  @Transactional
  public WorkOrderResponse update(Long id, WorkOrderRequest r) {
    WorkOrderEntity w = require(id);
    if (r.woDate() != null) w.setWoDate(r.woDate());
    apply(w, r);
    return toResponse(repository.save(w), vendors(List.of(w)));
  }

  private void apply(WorkOrderEntity w, WorkOrderRequest r) {
    w.setTitle(r.title().trim());
    w.setProjectId(r.projectId());
    if (r.vendorPartyId() != null) w.setVendorPartyId(r.vendorPartyId());
    if (w.getVendorPartyId() == null) throw new IllegalArgumentException("Pick the subcontractor for this work order.");
    if (r.status() != null && !r.status().isBlank()) w.setStatus(r.status());
    w.setStartDate(r.startDate());
    w.setEndDate(r.endDate());
    w.setTaxPercent(nz(r.taxPercent()));
    w.setDiscount(nz(r.discount()));
    w.setCharges(nz(r.charges()));
    w.setBankAccountName(r.bankAccountName());
    w.setBankAccountNumber(r.bankAccountNumber());
    w.setBankIfsc(r.bankIfsc());
    w.setTerms(r.terms());
    w.setNotes(r.notes());

    // Items are replaced wholesale, but a line that is still present keeps its id — otherwise every
    // edit of the order would wipe the progress recorded against its lines.
    Map<Long, WorkOrderItemEntity> existing =
        w.getItems().stream().filter(i -> i.getId() != null).collect(Collectors.toMap(WorkOrderItemEntity::getId, i -> i));
    List<WorkOrderItemEntity> keep = new ArrayList<>();
    int order = 0;
    if (r.items() != null) {
      for (WorkOrderItemRequest ir : r.items()) {
        if (ir.itemName() == null || ir.itemName().isBlank()) continue;
        WorkOrderItemEntity item = ir.id() != null ? existing.get(ir.id()) : null;
        if (item == null) {
          item = new WorkOrderItemEntity();
          item.setWorkOrder(w);
        }
        item.setItemId(ir.itemId());
        item.setItemName(ir.itemName().trim());
        item.setDescription(ir.description());
        item.setUnit(ir.unit());
        item.setDimN(ir.dimN());
        item.setDimL(ir.dimL());
        item.setDimW(ir.dimW());
        item.setDimH(ir.dimH());
        item.setQuantity(quantityOf(ir));
        item.setRate(nz(ir.rate()));
        if (ir.progressPercent() != null) item.setProgressPercent(clampPercent(ir.progressPercent()));
        item.setSortOrder(order++);
        keep.add(item);
      }
    }
    w.getItems().clear();
    w.getItems().addAll(keep);
  }

  /**
   * The measured quantity, or the typed one.
   *
   * <p>Any dimension given at all means the row was measured, and the product is the quantity —
   * a blank box in that case is a 1, not a zero, so 4 x 12.5 with no width or height is 50. A row
   * with no dimensions falls back to what was typed.
   */
  private static BigDecimal quantityOf(WorkOrderItemRequest ir) {
    boolean measured =
        positive(ir.dimN()) || positive(ir.dimL()) || positive(ir.dimW()) || positive(ir.dimH());
    if (!measured) return ir.quantity() == null ? BigDecimal.ONE : ir.quantity();
    BigDecimal q = BigDecimal.ONE;
    for (BigDecimal d : List.of(
        positive(ir.dimN()) ? ir.dimN() : BigDecimal.ONE,
        positive(ir.dimL()) ? ir.dimL() : BigDecimal.ONE,
        positive(ir.dimW()) ? ir.dimW() : BigDecimal.ONE,
        positive(ir.dimH()) ? ir.dimH() : BigDecimal.ONE)) {
      q = q.multiply(d);
    }
    return q.setScale(3, RoundingMode.HALF_UP);
  }

  private static BigDecimal clampPercent(BigDecimal v) {
    if (v.signum() < 0) return BigDecimal.ZERO;
    return v.compareTo(BigDecimal.valueOf(100)) > 0 ? BigDecimal.valueOf(100) : v;
  }

  @Transactional
  public void delete(Long id) {
    WorkOrderEntity w = require(id);
    if (!w.getBills().isEmpty()) {
      // Deleting an order that has been billed would leave money booked against nothing.
      throw new IllegalArgumentException(
          "This work order has " + w.getBills().size() + " bill(s) against it. Remove those first.");
    }
    repository.delete(w);
  }

  // ---- Progress ----

  @Transactional
  public WorkOrderResponse setProgress(Long id, Long itemId, ProgressRequest r) {
    WorkOrderEntity w = require(id);
    WorkOrderItemEntity item =
        w.getItems().stream()
            .filter(i -> i.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Line " + itemId + " is not on this work order"));
    item.setProgressPercent(clampPercent(nz(r.progressPercent())));
    return toResponse(repository.save(w), vendors(List.of(w)));
  }

  // ---- Bills ----

  @Transactional
  public WorkOrderResponse saveBill(Long id, SubconBillRequest r, Long userId) {
    WorkOrderEntity w = require(id);
    SubconBillEntity b =
        r.id() == null
            ? null
            : w.getBills().stream().filter(x -> x.getId().equals(r.id())).findFirst().orElse(null);
    if (b == null) {
      b = new SubconBillEntity();
      b.setCreatedBy(userId);
      w.addBill(b);
    }
    b.setBillNo(r.billNo());
    b.setBillDate(r.billDate() != null ? r.billDate() : LocalDate.now().toString());
    b.setAmount(nz(r.amount()));
    b.setRetention(nz(r.retention()));
    b.setMaterialRecovery(nz(r.materialRecovery()));
    b.setNote(r.note());
    b.setVyaparInvoiceId(r.vyaparInvoiceId());
    return toResponse(repository.save(w), vendors(List.of(w)));
  }

  @Transactional
  public WorkOrderResponse deleteBill(Long id, Long billId) {
    WorkOrderEntity w = require(id);
    w.getBills().removeIf(b -> b.getId().equals(billId));
    return toResponse(repository.save(w), vendors(List.of(w)));
  }

  // ---- Material issued to him ----

  @Transactional
  public WorkOrderResponse saveMaterial(Long id, SubconMaterialRequest r, Long userId) {
    WorkOrderEntity w = require(id);
    SubconMaterialEntity m =
        r.id() == null
            ? null
            : w.getMaterials().stream().filter(x -> x.getId().equals(r.id())).findFirst().orElse(null);
    if (m == null) {
      m = new SubconMaterialEntity();
      m.setCreatedBy(userId);
      w.addMaterial(m);
    }
    m.setItemId(r.itemId());
    m.setItemName(r.itemName().trim());
    m.setUnit(r.unit());
    String movement = r.movement() == null ? "ISSUE" : r.movement().toUpperCase();
    if (!List.of("ISSUE", "RETURN", "CONSUMED").contains(movement)) {
      throw new IllegalArgumentException("Movement must be ISSUE, RETURN or CONSUMED.");
    }
    m.setMovement(movement);
    m.setQuantity(nz(r.quantity()));
    m.setRate(nz(r.rate()));
    m.setMovedOn(r.movedOn() != null ? r.movedOn() : LocalDate.now().toString());
    m.setNote(r.note());
    return toResponse(repository.save(w), vendors(List.of(w)));
  }

  @Transactional
  public WorkOrderResponse deleteMaterial(Long id, Long materialId) {
    WorkOrderEntity w = require(id);
    w.getMaterials().removeIf(m -> m.getId().equals(materialId));
    return toResponse(repository.save(w), vendors(List.of(w)));
  }

  // ================= Helpers =================

  private WorkOrderEntity require(Long id) {
    return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Work order " + id + " not found"));
  }

  private String nextNumber() {
    String prefix = "WO-" + LocalDate.now().getYear() + "-";
    return prefix + String.format("%03d", repository.countByWoNoStartingWith(prefix) + 1);
  }

  private WorkOrderResponse toResponse(WorkOrderEntity w, Map<Long, PartyEntity> vendors) {
    List<WorkOrderItemResponse> items =
        w.getItems().stream()
            .sorted(java.util.Comparator.comparingInt(WorkOrderItemEntity::getSortOrder))
            .map(
                i ->
                    new WorkOrderItemResponse(
                        i.getId(),
                        i.getItemId(),
                        i.getItemName(),
                        i.getDescription(),
                        i.getUnit(),
                        i.getDimN(),
                        i.getDimL(),
                        i.getDimW(),
                        i.getDimH(),
                        i.getQuantity(),
                        i.getRate(),
                        i.getQuantity().multiply(i.getRate()).setScale(2, RoundingMode.HALF_UP),
                        i.getProgressPercent(),
                        i.getSortOrder()))
            .toList();

    BigDecimal itemSubTotal =
        items.stream().map(WorkOrderItemResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal afterTerms = itemSubTotal.subtract(w.getDiscount()).add(w.getCharges());
    BigDecimal taxAmount =
        afterTerms.multiply(w.getTaxPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    BigDecimal orderValue = afterTerms.add(taxAmount);

    // Weighted by line value: a 90%-done line worth ₹4.4L is not the same as a 100%-done ₹3,600 one.
    BigDecimal earned =
        items.stream()
            .map(i -> i.amount().multiply(i.progressPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal progress =
        itemSubTotal.signum() == 0
            ? BigDecimal.ZERO
            : earned.multiply(BigDecimal.valueOf(100)).divide(itemSubTotal, 2, RoundingMode.HALF_UP);

    List<SubconBillResponse> bills =
        w.getBills().stream()
            .map(
                b ->
                    new SubconBillResponse(
                        b.getId(),
                        b.getBillNo(),
                        b.getBillDate(),
                        b.getAmount(),
                        b.getRetention(),
                        b.getMaterialRecovery(),
                        b.getAmount().subtract(b.getRetention()).subtract(b.getMaterialRecovery()),
                        b.getNote(),
                        b.getVyaparInvoiceId()))
            .toList();

    BigDecimal billed = bills.stream().map(SubconBillResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal retentionHeld =
        bills.stream().map(SubconBillResponse::retention).reduce(BigDecimal.ZERO, BigDecimal::add);

    List<SubconMaterialResponse> materials =
        w.getMaterials().stream()
            .map(
                m ->
                    new SubconMaterialResponse(
                        m.getId(),
                        m.getItemId(),
                        m.getItemName(),
                        m.getUnit(),
                        m.getMovement(),
                        m.getQuantity(),
                        m.getRate(),
                        m.getMovedOn(),
                        m.getNote()))
            .toList();

    // The Materials tab reads per material, not per movement: "he has had 200 bags and still holds
    // 40" is the question, and it can only be answered by rolling the movements up.
    Map<String, BigDecimal[]> roll = new LinkedHashMap<>();
    Map<String, String> units = new HashMap<>();
    for (SubconMaterialEntity m : w.getMaterials()) {
      BigDecimal[] cur = roll.computeIfAbsent(m.getItemName(), k -> new BigDecimal[] {
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
      });
      units.putIfAbsent(m.getItemName(), m.getUnit());
      switch (m.getMovement()) {
        case "RETURN" -> cur[1] = cur[1].add(m.getQuantity());
        case "CONSUMED" -> cur[2] = cur[2].add(m.getQuantity());
        default -> {
          cur[0] = cur[0].add(m.getQuantity());
          cur[3] = cur[3].add(m.getQuantity().multiply(m.getRate()));
        }
      }
    }
    List<SubconMaterialSummary> summary =
        roll.entrySet().stream()
            .map(
                e -> {
                  BigDecimal[] v = e.getValue();
                  return new SubconMaterialSummary(
                      e.getKey(),
                      units.get(e.getKey()),
                      v[0],
                      v[1],
                      v[2],
                      v[0].subtract(v[1]).subtract(v[2]),
                      v[3].setScale(2, RoundingMode.HALF_UP));
                })
            .toList();
    BigDecimal materialIssuedValue =
        summary.stream().map(SubconMaterialSummary::issuedValue).reduce(BigDecimal.ZERO, BigDecimal::add);

    PartyEntity vendor = vendors.get(w.getVendorPartyId());

    return new WorkOrderResponse(
        w.getId(),
        w.getWoNo(),
        w.getTitle(),
        w.getProjectId(),
        w.getVendorPartyId(),
        vendor == null ? "Vendor " + w.getVendorPartyId() : vendor.getName(),
        vendor == null ? null : vendor.getPhone(),
        w.getStatus(),
        w.getWoDate(),
        w.getStartDate(),
        w.getEndDate(),
        w.getTaxPercent(),
        w.getDiscount(),
        w.getCharges(),
        w.getBankAccountName(),
        w.getBankAccountNumber(),
        w.getBankIfsc(),
        w.getTerms(),
        w.getNotes(),
        itemSubTotal,
        taxAmount,
        orderValue,
        progress,
        earned,
        billed,
        orderValue.subtract(billed),
        retentionHeld,
        materialIssuedValue,
        items,
        bills,
        materials,
        summary);
  }
}
