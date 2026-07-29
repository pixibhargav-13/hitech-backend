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

/** A named set of leave types with counts — assignable to members via their payroll profile. */
@Getter
@Setter
@Entity
@Table(name = "payroll_leave_policies")
public class LeavePolicyEntity extends BaseEntity {

  @Column(nullable = false, length = 120)
  private String name;

  /** YEARLY or MONTHLY. */
  @Column(nullable = false, length = 10)
  private String cycle = "YEARLY";

  @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id ASC")
  private List<LeaveTypeEntity> types = new ArrayList<>();
}
