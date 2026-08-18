package com.hitech.erp.approval.service;

import com.hitech.erp.approval.db.ApprovalEntities.Action;
import com.hitech.erp.approval.db.ApprovalEntities.ActionLog;
import com.hitech.erp.approval.db.ApprovalEntities.Chain;
import com.hitech.erp.approval.db.ApprovalEntities.Level;
import com.hitech.erp.approval.db.ApprovalEntities.Mode;
import com.hitech.erp.approval.db.ApprovalEntities.Request;
import com.hitech.erp.approval.db.ApprovalEntities.RequestLevel;
import com.hitech.erp.approval.db.ApprovalEntities.Status;
import com.hitech.erp.approval.db.ApprovalEntityType;
import com.hitech.erp.approval.db.ApprovalChainRepository;
import com.hitech.erp.approval.db.ApprovalRequestRepository;
import com.hitech.erp.approval.dto.ApprovalDtos.ActionEntry;
import com.hitech.erp.approval.dto.ApprovalDtos.ApprovalStateResponse;
import com.hitech.erp.approval.dto.ApprovalDtos.LevelState;
import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import com.hitech.erp.usermanagement.db.RoleEntity;
import com.hitech.erp.usermanagement.db.RoleRepository;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives multi-level approvals for any entity type.
 *
 * <p>Features don't implement approval themselves any more — they call {@link #submit} when a record
 * is raised and {@link #decide} when someone acts, then read {@link #state} to render the sidebar.
 * The chain, the sequencing and the audit trail are all handled here, so leave, purchase orders and
 * payroll runs behave identically and only have to agree on an {@link ApprovalEntityType}.
 *
 * <p><b>Fallback is deliberate.</b> If a type has no published chain, {@link #submit} returns empty
 * and the caller keeps its existing single-step behaviour. That's what makes this safe to deploy
 * before anyone has configured anything.
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

  /** Guards against a corrupt {@code reports_to_role_id} loop while climbing the ladder. */
  private static final int MAX_LADDER_DEPTH = 20;

  private final ApprovalChainRepository chainRepository;
  private final ApprovalRequestRepository requestRepository;
  private final RoleRepository roleRepository;
  private final AppUserRepository userRepository;

  // ================= Submitting =================

  /**
   * Raise an approval for a record, if its type has a published chain.
   *
   * @return the new request, or empty when no chain applies — the caller then handles the record
   *     the way it did before approvals existed.
   */
  @Transactional
  public Optional<Request> submit(ApprovalEntityType type, Long entityId, AuthenticatedUser requester) {
    Chain chain = chainRepository.findByEntityType(type.name()).orElse(null);
    if (chain == null || !chain.isPublished()) return Optional.empty();

    List<LevelSpec> specs = resolveLevels(chain, requester);
    if (specs.isEmpty()) return Optional.empty(); // published but nobody above them — nothing to do

    // Re-raising replaces any previous attempt on the same record (e.g. a rejected request that
    // the member edited and resubmitted). The unique constraint makes this explicit.
    requestRepository.findByEntityTypeAndEntityId(type.name(), entityId).ifPresent(requestRepository::delete);
    requestRepository.flush();

    Request request = new Request();
    request.setEntityType(type.name());
    request.setEntityId(entityId);
    request.setRequestedBy(requester.id());
    request.setStatus(Status.PENDING.name());
    request.setCurrentLevel(1);
    request.setTotalLevels(specs.size());

    for (LevelSpec spec : specs) {
      for (Long roleId : spec.roleIds()) {
        RequestLevel rl = new RequestLevel();
        rl.setRequest(request);
        rl.setLevelOrder(spec.order());
        rl.setRoleId(roleId);
        rl.setRoleName(roleName(roleId));
        request.getLevels().add(rl);
      }
    }

    request.getActions().add(log(request, null, Action.SUBMITTED, requester, "Submitted for approval"));
    return Optional.of(requestRepository.save(request));
  }

  /**
   * The rungs this request will climb.
   *
   * <p>EXPLICIT reads the admin's configuration. REPORTING_CHAIN walks {@code reports_to_role_id}
   * upward from the requester's own role — so a Team Member under a Project Manager under a Super
   * Admin produces level 1 = Project Manager, level 2 = Super Admin, exactly the ladder configured
   * in Roles &amp; Access, with no separate chain to keep in sync.
   */
  private List<LevelSpec> resolveLevels(Chain chain, AuthenticatedUser requester) {
    if (Mode.REPORTING_CHAIN.name().equals(chain.getMode())) {
      List<LevelSpec> out = new ArrayList<>();
      int order = 1;
      Long roleId = requester.roleId();
      Set<Long> seen = new HashSet<>();
      for (int depth = 0; depth < MAX_LADDER_DEPTH; depth++) {
        RoleEntity role = roleId == null ? null : roleRepository.findById(roleId).orElse(null);
        if (role == null || role.getReportsToRoleId() == null) break;
        Long parent = role.getReportsToRoleId();
        if (!seen.add(parent)) break; // cycle in the ladder — stop rather than spin
        out.add(new LevelSpec(order++, List.of(parent)));
        roleId = parent;
      }
      return out;
    }

    List<LevelSpec> out = new ArrayList<>();
    int order = 1;
    for (Level level : chain.getLevels()) {
      if (level.getRoleIds().isEmpty()) continue; // an empty rung would block forever
      out.add(new LevelSpec(order++, List.copyOf(level.getRoleIds())));
    }
    return out;
  }

  private record LevelSpec(int order, List<Long> roleIds) {}

  // ================= Deciding =================

  /**
   * Record one decision. Approving the last rung approves the whole request; a rejection at any rung
   * ends it immediately — this is an all-must-agree chain, which is what a sign-off ladder means.
   *
   * @return the request's status after the decision
   */
  @Transactional
  public Status decide(ApprovalEntityType type, Long entityId, AuthenticatedUser actor, boolean approve, String note) {
    Request request = requestRepository
        .findByEntityTypeAndEntityId(type.name(), entityId)
        .orElseThrow(() -> new EntityNotFoundException("No approval is pending for this record."));

    if (!Status.PENDING.name().equals(request.getStatus())) {
      throw new IllegalStateException("This request is already " + request.getStatus().toLowerCase() + ".");
    }
    if (!canAct(request, actor)) {
      throw new AccessDeniedException("This approval is not waiting on your role.");
    }

    request.getActions().add(
        log(request, request.getCurrentLevel(), approve ? Action.APPROVED : Action.REJECTED, actor, note));

    if (!approve) {
      request.setStatus(Status.REJECTED.name());
    } else if (request.getCurrentLevel() >= request.getTotalLevels()) {
      request.setStatus(Status.APPROVED.name());
    } else {
      request.setCurrentLevel(request.getCurrentLevel() + 1);
    }

    requestRepository.save(request);
    return Status.valueOf(request.getStatus());
  }

  /** Withdraw a request — only the person who raised it, and only while it's still pending. */
  @Transactional
  public void cancel(ApprovalEntityType type, Long entityId, AuthenticatedUser actor) {
    requestRepository.findByEntityTypeAndEntityId(type.name(), entityId).ifPresent(request -> {
      if (!Status.PENDING.name().equals(request.getStatus())) return;
      request.setStatus(Status.CANCELLED.name());
      request.getActions().add(log(request, request.getCurrentLevel(), Action.CANCELLED, actor, "Withdrawn"));
      requestRepository.save(request);
    });
  }

  /** True when this request is sitting on a rung the actor's role can clear. */
  public boolean canAct(Request request, AuthenticatedUser actor) {
    if (actor == null || !Status.PENDING.name().equals(request.getStatus())) return false;
    // Nobody signs off their own request, even if their role appears on the rung.
    if (actor.id() != null && actor.id().equals(request.getRequestedBy())) return false;
    return request.getLevels().stream()
        .anyMatch(l -> l.getLevelOrder() == request.getCurrentLevel()
            && l.getRoleId() != null
            && l.getRoleId().equals(actor.roleId()));
  }

  // ================= Reading =================

  /** Everything waiting on the actor's role right now, across one entity type. */
  @Transactional(readOnly = true)
  public List<Request> awaitingMe(ApprovalEntityType type, AuthenticatedUser actor) {
    if (actor == null || actor.roleId() == null) return List.of();
    return requestRepository.findAwaitingRoles(List.of(actor.roleId())).stream()
        .filter(r -> r.getEntityType().equals(type.name()))
        .filter(r -> !actor.id().equals(r.getRequestedBy()))
        .toList();
  }

  /** The approval state of one record, or null when it never went through a chain. */
  @Transactional(readOnly = true)
  public ApprovalStateResponse state(ApprovalEntityType type, Long entityId, AuthenticatedUser actor) {
    return requestRepository
        .findByEntityTypeAndEntityId(type.name(), entityId)
        .map(r -> toState(r, actor))
        .orElse(null);
  }

  /** Bulk variant — one query for a whole list screen instead of one per row. */
  @Transactional(readOnly = true)
  public Map<Long, ApprovalStateResponse> statesFor(
      ApprovalEntityType type, List<Long> entityIds, AuthenticatedUser actor) {
    if (entityIds.isEmpty()) return Map.of();
    Map<Long, ApprovalStateResponse> out = new LinkedHashMap<>();
    for (Request r : requestRepository.findByEntityTypeAndEntityIdIn(type.name(), entityIds)) {
      out.put(r.getEntityId(), toState(r, actor));
    }
    return out;
  }

  public ApprovalStateResponse toState(Request r, AuthenticatedUser actor) {
    // Group the frozen rungs by order so each renders as one row with its roles.
    Map<Integer, List<String>> rolesByLevel = new LinkedHashMap<>();
    for (RequestLevel l : r.getLevels()) {
      rolesByLevel.computeIfAbsent(l.getLevelOrder(), k -> new ArrayList<>())
          .add(l.getRoleName() == null ? "Unknown role" : l.getRoleName());
    }

    // The decision that closed each rung, for the "approved by X on Y" line.
    Map<Integer, ActionLog> decisions = new LinkedHashMap<>();
    for (ActionLog a : r.getActions()) {
      if (a.getLevelOrder() == null) continue;
      if (Action.APPROVED.name().equals(a.getAction()) || Action.REJECTED.name().equals(a.getAction())) {
        decisions.put(a.getLevelOrder(), a);
      }
    }

    List<LevelState> levels = new ArrayList<>();
    for (Map.Entry<Integer, List<String>> e : rolesByLevel.entrySet()) {
      int order = e.getKey();
      ActionLog decision = decisions.get(order);
      String state;
      if (decision != null) {
        state = decision.getAction(); // APPROVED or REJECTED
      } else if (Status.PENDING.name().equals(r.getStatus()) && order == r.getCurrentLevel()) {
        state = "PENDING";
      } else if (Status.CANCELLED.name().equals(r.getStatus())) {
        state = "CANCELLED";
      } else {
        state = "WAITING";
      }
      levels.add(new LevelState(
          order,
          e.getValue(),
          state,
          decision == null ? null : decision.getActorName(),
          decision == null || decision.getCreatedAt() == null ? null : decision.getCreatedAt().toString(),
          decision == null ? null : decision.getNote()));
    }

    List<ActionEntry> trail = r.getActions().stream()
        .map(a -> new ActionEntry(
            a.getId(), a.getLevelOrder(), a.getActorUserId(), a.getActorName(), a.getActorRole(),
            a.getAction(), a.getNote(), a.getCreatedAt() == null ? null : a.getCreatedAt().toString()))
        .toList();

    String awaiting = Status.PENDING.name().equals(r.getStatus())
        ? String.join(" or ", new LinkedHashSet<>(rolesByLevel.getOrDefault(r.getCurrentLevel(), List.of())))
        : null;

    return new ApprovalStateResponse(
        r.getId(), r.getEntityType(), r.getEntityId(), r.getStatus(),
        r.getCurrentLevel(), r.getTotalLevels(), canAct(r, actor), awaiting, levels, trail);
  }

  // ================= helpers =================

  private ActionLog log(Request request, Integer level, Action action, AuthenticatedUser actor, String note) {
    ActionLog a = new ActionLog();
    a.setRequest(request);
    a.setLevelOrder(level);
    a.setAction(action.name());
    a.setNote(note == null || note.isBlank() ? null : note.trim());
    a.setCreatedAt(LocalDateTime.now());
    if (actor != null) {
      a.setActorUserId(actor.id());
      a.setActorName(actor.fullName());
      a.setActorRole(actor.roleName());
    }
    return a;
  }

  private String roleName(Long roleId) {
    return roleRepository.findById(roleId).map(RoleEntity::getName).orElse(null);
  }

  /** Display names for the members behind a set of ids — used by callers rendering a trail. */
  @Transactional(readOnly = true)
  public Map<Long, String> namesOf(Set<Long> userIds) {
    if (userIds.isEmpty()) return Map.of();
    return userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(AppUserEntity::getId, AppUserEntity::getFullName, (a, b) -> a));
  }
}
