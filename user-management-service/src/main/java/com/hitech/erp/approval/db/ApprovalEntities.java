package com.hitech.erp.approval.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** JPA types for the multi-level approval framework. Grouped to keep the module compact. */
public final class ApprovalEntities {

  private ApprovalEntities() {}

  /** How a chain decides who approves. */
  public enum Mode {
    /** Levels are configured by an admin — the Onsite-style screen. */
    EXPLICIT,
    /**
     * Levels are derived at submit time by walking {@code roles.reports_to_role_id} up from the
     * requester. The ladder in Roles &amp; Access <em>is</em> the approval path, so it can't drift
     * out of sync with a separately-maintained chain.
     */
    REPORTING_CHAIN
  }

  public enum Status {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
  }

  public enum Action {
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED
  }

  /** One approvable entity type's configuration. Exactly one row per {@link ApprovalEntityType}. */
  @Getter
  @Setter
  @Entity(name = "ApprovalChain")
  @Table(name = "approval_chains")
  public static class Chain extends BaseEntity {

    @Column(name = "entity_type", nullable = false, length = 60, unique = true)
    private String entityType;

    @Column(nullable = false, length = 20)
    private String mode = Mode.EXPLICIT.name();

    /** Unpublished chains are ignored entirely — the entity keeps its pre-existing behaviour. */
    @Column(nullable = false)
    private boolean published = false;

    @OneToMany(mappedBy = "chain", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("levelOrder ASC")
    private List<Level> levels = new ArrayList<>();
  }

  /** One rung of a chain. Any of its roles may approve; rungs are climbed in order. */
  @Getter
  @Setter
  @Entity(name = "ApprovalLevel")
  @Table(name = "approval_levels")
  public static class Level extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chain_id", nullable = false)
    private Chain chain;

    @Column(name = "level_order", nullable = false)
    private int levelOrder;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "approval_level_roles", joinColumns = @JoinColumn(name = "level_id"))
    @Column(name = "role_id", nullable = false)
    private Set<Long> roleIds = new LinkedHashSet<>();
  }

  /** A live approval against one record. Unique on (entityType, entityId). */
  @Getter
  @Setter
  @Entity(name = "ApprovalRequest")
  @Table(name = "approval_requests")
  public static class Request extends BaseEntity {

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(nullable = false, length = 15)
    private String status = Status.PENDING.name();

    /** The rung currently awaiting a decision. Meaningless once status leaves PENDING. */
    @Column(name = "current_level", nullable = false)
    private int currentLevel = 1;

    @Column(name = "total_levels", nullable = false)
    private int totalLevels = 1;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("levelOrder ASC, id ASC")
    private List<RequestLevel> levels = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<ActionLog> actions = new ArrayList<>();
  }

  /**
   * Who may approve one rung <em>of this request</em>, frozen when it was raised.
   *
   * <p>Without the snapshot, an admin reordering the chain would retroactively change who was
   * supposed to sign off on requests already in flight — and the audit trail would describe a path
   * the request never actually took.
   */
  @Getter
  @Setter
  @Entity(name = "ApprovalRequestLevel")
  @Table(name = "approval_request_levels")
  public static class RequestLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @Column(name = "level_order", nullable = false)
    private int levelOrder;

    @Column(name = "role_id")
    private Long roleId;

    /** Denormalised so the trail still reads correctly after a role is renamed or deleted. */
    @Column(name = "role_name", length = 120)
    private String roleName;
  }

  /** Append-only audit row — what the approval sidebar renders. */
  @Getter
  @Setter
  @Entity(name = "ApprovalActionLog")
  @Table(name = "approval_actions")
  public static class ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @Column(name = "level_order")
    private Integer levelOrder;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name", length = 255)
    private String actorName;

    @Column(name = "actor_role", length = 120)
    private String actorRole;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
  }
}
