package com.hitech.erp.usermanagement.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "roles")
public class RoleEntity extends BaseEntity {

  @Column(nullable = false, unique = true, length = 100)
  private String name;

  @Column(length = 500)
  private String description;

  /** System roles (e.g. Super Admin) are seeded and cannot be deleted or renamed away. */
  @Column(name = "is_system", nullable = false)
  private boolean isSystem;

  /**
   * Parent role in the org ladder (this role reports to it). Null = top of the hierarchy. Stored as
   * a plain id rather than a self-association to keep loading simple and avoid recursive fetches;
   * the frontend builds the tree from the flat list.
   */
  @Column(name = "reports_to_role_id")
  private Long reportsToRoleId;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  private Set<PermissionEntity> permissions = new HashSet<>();
}
