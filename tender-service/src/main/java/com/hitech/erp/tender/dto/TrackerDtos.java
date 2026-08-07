package com.hitech.erp.tender.dto;

/**
 * Request/response shapes for the tender tracker sub-collections (status milestones, documentation,
 * hardcopy dispatch, materials). Dynamic per-step / per-document data rides as opaque JSON strings so
 * the runtime-editable step lists don't need a schema change.
 */
public final class TrackerDtos {

  private TrackerDtos() {}

  // ---- Status tracker (milestones) ----
  public record MilestoneRequest(
      String tenderRef, String nameOfWork, String workStartDate, String progress, String stepsJson) {}

  public record MilestoneResponse(
      Long id,
      String tenderRef,
      String nameOfWork,
      String workStartDate,
      String progress,
      String stepsJson) {}

  // ---- Documentation tracker ----
  public record DocumentRequest(
      String tenderRef,
      String nameOfWork,
      String progress,
      String viewDocuments,
      String pairsJson,
      String raBillsJson) {}

  public record DocumentResponse(
      Long id,
      String tenderRef,
      String nameOfWork,
      String progress,
      String viewDocuments,
      String pairsJson,
      String raBillsJson) {}

  // ---- Hardcopy dispatch ----
  public record HardcopyRequest(
      String date,
      String nameOfWork,
      String tenderRef,
      String documentList,
      String packedBy,
      String dispatchBy,
      String trackingNo,
      String arrived,
      String arrivedDate,
      String remarks) {}

  public record HardcopyResponse(
      Long id,
      String date,
      String nameOfWork,
      String tenderRef,
      String documentList,
      String packedBy,
      String dispatchBy,
      String trackingNo,
      String arrived,
      String arrivedDate,
      String remarks) {}

  // ---- Materials ----
  public record MaterialRequest(
      String party, String manufacturerType, String make, String location, String contact, String email) {}

  public record MaterialResponse(
      Long id,
      String party,
      String manufacturerType,
      String make,
      String location,
      String contact,
      String email) {}
}
