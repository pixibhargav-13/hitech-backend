package com.hitech.erp.approval.service;

import com.hitech.erp.approval.db.ApprovalEntities.Chain;
import com.hitech.erp.approval.db.ApprovalEntities.Level;
import com.hitech.erp.approval.db.ApprovalEntities.Mode;
import com.hitech.erp.approval.db.ApprovalEntityType;
import com.hitech.erp.approval.db.ApprovalChainRepository;
import com.hitech.erp.approval.dto.ApprovalDtos.ChainResponse;
import com.hitech.erp.approval.dto.ApprovalDtos.ChainUpdateRequest;
import com.hitech.erp.approval.dto.ApprovalDtos.LevelConfig;
import com.hitech.erp.approval.dto.ApprovalDtos.LevelInput;
import com.hitech.erp.usermanagement.db.RoleEntity;
import com.hitech.erp.usermanagement.db.RoleRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read/write side of Settings → Multi Level Approval. */
@Service
@RequiredArgsConstructor
public class ApprovalChainService {

  private final ApprovalChainRepository chainRepository;
  private final RoleRepository roleRepository;

  /**
   * Every approvable type, configured or not.
   *
   * <p>Types with no row yet are returned as empty unpublished chains rather than omitted, so the
   * settings screen lists the full catalogue the way Onsite does and an admin can configure one
   * without a "create" step.
   */
  @Transactional(readOnly = true)
  public List<ChainResponse> getAll() {
    Map<String, Chain> existing = new HashMap<>();
    for (Chain c : chainRepository.findAll()) existing.put(c.getEntityType(), c);
    Map<Long, String> roleNames = roleNames();

    List<ChainResponse> out = new ArrayList<>();
    for (ApprovalEntityType type : ApprovalEntityType.values()) {
      Chain c = existing.get(type.name());
      out.add(c == null
          ? new ChainResponse(null, type.name(), type.label(), Mode.EXPLICIT.name(), false, List.of())
          : toResponse(c, type, roleNames));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public ChainResponse get(String entityType) {
    ApprovalEntityType type = requireType(entityType);
    Map<Long, String> roleNames = roleNames();
    return chainRepository
        .findByEntityType(type.name())
        .map(c -> toResponse(c, type, roleNames))
        .orElse(new ChainResponse(null, type.name(), type.label(), Mode.EXPLICIT.name(), false, List.of()));
  }

  /**
   * Replace a chain's configuration wholesale — the settings screen always sends the entire ladder,
   * which keeps reordering and deletion trivial and avoids a per-level diffing API.
   *
   * <p>Existing in-flight requests are untouched: they carry their own frozen copy of the levels.
   */
  @Transactional
  public ChainResponse save(String entityType, ChainUpdateRequest r) {
    ApprovalEntityType type = requireType(entityType);
    Chain chain = chainRepository.findByEntityType(type.name()).orElseGet(() -> {
      Chain fresh = new Chain();
      fresh.setEntityType(type.name());
      return fresh;
    });

    if (r.mode() != null) {
      chain.setMode(parseMode(r.mode()).name());
    }
    if (r.published() != null) {
      chain.setPublished(r.published());
    }

    if (r.levels() != null) {
      chain.getLevels().clear();
      int order = 1;
      for (LevelInput input : r.levels()) {
        if (input == null || input.roleIds() == null || input.roleIds().isEmpty()) {
          // A rung with no approver would stall every request that reached it.
          throw new IllegalArgumentException("Level " + order + " needs at least one approver role.");
        }
        Level level = new Level();
        level.setChain(chain);
        level.setLevelOrder(order++);
        level.setRoleIds(new LinkedHashSet<>(input.roleIds()));
        chain.getLevels().add(level);
      }
    }

    boolean explicit = Mode.EXPLICIT.name().equals(chain.getMode());
    if (chain.isPublished() && explicit && chain.getLevels().isEmpty()) {
      throw new IllegalArgumentException("Add at least one approval level before publishing.");
    }

    return toResponse(chainRepository.save(chain), type, roleNames());
  }

  // ---- helpers ----

  private ChainResponse toResponse(Chain c, ApprovalEntityType type, Map<Long, String> roleNames) {
    List<LevelConfig> levels = c.getLevels().stream()
        .map(l -> new LevelConfig(
            l.getLevelOrder(),
            List.copyOf(l.getRoleIds()),
            l.getRoleIds().stream().map(id -> roleNames.getOrDefault(id, "Deleted role")).toList()))
        .toList();
    return new ChainResponse(c.getId(), type.name(), type.label(), c.getMode(), c.isPublished(), levels);
  }

  private Map<Long, String> roleNames() {
    Map<Long, String> out = new HashMap<>();
    for (RoleEntity r : roleRepository.findAll()) out.put(r.getId(), r.getName());
    return out;
  }

  private static ApprovalEntityType requireType(String value) {
    return ApprovalEntityType.from(value)
        .orElseThrow(() -> new IllegalArgumentException("Unknown approval entity type: " + value));
  }

  private static Mode parseMode(String value) {
    try {
      return Mode.valueOf(value.trim().toUpperCase().replace(' ', '_').replace('-', '_'));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid approval mode: " + value);
    }
  }
}
