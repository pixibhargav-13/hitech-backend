package com.hitech.erp.approval.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Wire shapes for the approval framework. */
public final class ApprovalDtos {

  private ApprovalDtos() {}

  // ---- Configuration (Settings → Multi Level Approval) ----

  public record LevelConfig(int levelOrder, List<Long> roleIds, List<String> roleNames) {}

  public record ChainResponse(
      Long id,
      String entityType,
      String entityLabel,
      String mode,
      boolean published,
      List<LevelConfig> levels) {}

  /** Full replace of a chain's levels — the settings screen always sends the whole ladder. */
  public record ChainUpdateRequest(String mode, Boolean published, List<LevelInput> levels) {}

  public record LevelInput(@NotNull List<Long> roleIds) {}

  // ---- Running approvals ----

  /** One entry in the sidebar's audit trail. */
  public record ActionEntry(
      Long id,
      Integer levelOrder,
      Long actorUserId,
      String actorName,
      String actorRole,
      String action,
      String note,
      String at) {}

  /** A rung, with whether it's done, current, or still ahead. */
  public record LevelState(
      int levelOrder,
      List<String> roleNames,
      /** PENDING | APPROVED | REJECTED | WAITING — WAITING means an earlier rung hasn't cleared. */
      String state,
      String decidedBy,
      String decidedAt,
      String note) {}

  /**
   * Everything the approval sidebar needs for one record. {@code canActNow} is the caller's own
   * ability to decide it right now — the UI shows Approve/Reject only when it is true.
   */
  public record ApprovalStateResponse(
      Long requestId,
      String entityType,
      Long entityId,
      String status,
      int currentLevel,
      int totalLevels,
      boolean canActNow,
      String awaitingRoleNames,
      List<LevelState> levels,
      List<ActionEntry> trail) {}

  public record DecisionRequest(@NotNull String action, String note) {}
}
