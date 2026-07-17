package com.hitech.erp.project.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A node in a project's location tree (e.g. Block A → Floor 1 → Unit 101).
 * Self-referential via parentId; a null parentId means a top-level location.
 */
@Getter
@Setter
@Entity
@Table(name = "project_locations")
public class ProjectLocationEntity extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private Long projectId;

  @Column(name = "parent_id")
  private Long parentId;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 0;
}
