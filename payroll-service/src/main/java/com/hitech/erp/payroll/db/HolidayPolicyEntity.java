package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** A named holiday calendar for a year — assignable to members via their payroll profile. */
@Getter
@Setter
@Entity
@Table(name = "payroll_holiday_policies")
public class HolidayPolicyEntity extends BaseEntity {

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false)
  private int year;

  @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("date ASC")
  private List<HolidayEntity> holidays = new ArrayList<>();
}
