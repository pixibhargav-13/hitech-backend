package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** One holiday within a {@link HolidayPolicyEntity}'s calendar. */
@Getter
@Setter
@Entity
@Table(name = "payroll_holidays")
public class HolidayEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "policy_id", nullable = false)
  private HolidayPolicyEntity policy;

  @Column(nullable = false)
  private LocalDate date;

  @Column(nullable = false, length = 200)
  private String name;

  /** PUBLIC or OPTIONAL. */
  @Column(nullable = false, length = 10)
  private String type = "PUBLIC";
}
