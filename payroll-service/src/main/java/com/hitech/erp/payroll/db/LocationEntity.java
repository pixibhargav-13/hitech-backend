package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A work-site geofence: a named polygon (JSON list of {lat,lng} vertices) plus the ids of the
 * members allowed to punch there (JSON list). Punch-in/out is enforced to be inside an assigned
 * site — see LocationService.
 */
@Getter
@Setter
@Entity
@Table(name = "payroll_locations")
public class LocationEntity extends BaseEntity {

  @Column(nullable = false, columnDefinition = "TEXT")
  private String name;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String points;

  @Column(name = "member_ids", columnDefinition = "TEXT")
  private String memberIds;

  /** Optional link to a project — when set, every member of that project can punch here too. */
  @Column(name = "project_id")
  private Long projectId;
}
