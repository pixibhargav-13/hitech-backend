package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * A member's enrolled reference face for punch verification: the 128-float descriptor (comma-joined)
 * plus a small selfie. One row per member — punches are matched against this on the client.
 */
@Getter
@Setter
@Entity
@Table(
    name = "payroll_face_enrollment",
    uniqueConstraints = @UniqueConstraint(name = "uq_payroll_face_enrollment_user", columnNames = "user_id"))
public class FaceEnrollmentEntity extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String descriptor;

  @Column(columnDefinition = "TEXT")
  private String photo;
}
