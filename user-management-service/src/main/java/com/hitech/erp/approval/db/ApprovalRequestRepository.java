package com.hitech.erp.approval.db;

import com.hitech.erp.approval.db.ApprovalEntities.Request;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Live approvals. */
public interface ApprovalRequestRepository extends JpaRepository<Request, Long> {

  Optional<Request> findByEntityTypeAndEntityId(String entityType, Long entityId);

  List<Request> findByEntityTypeAndEntityIdIn(String entityType, List<Long> entityIds);

  List<Request> findByRequestedByOrderByIdDesc(Long requestedBy);

  /**
   * Requests waiting on one of the caller's roles, at the rung they are currently on.
   *
   * <p>The {@code levelOrder = currentLevel} match is what makes the chain sequential: an HR head
   * configured at level 2 sees nothing until the level-1 PM has signed off and the request has
   * advanced.
   */
  @Query("""
      SELECT DISTINCT r FROM ApprovalRequest r
      JOIN r.levels l
      WHERE r.status = 'PENDING'
        AND l.levelOrder = r.currentLevel
        AND l.roleId IN :roleIds
      ORDER BY r.id DESC
      """)
  List<Request> findAwaitingRoles(@Param("roleIds") List<Long> roleIds);
}
