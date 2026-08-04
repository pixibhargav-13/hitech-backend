package com.hitech.erp.payroll.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The org-wide default salary-component template new employees inherit. A single row; components
 * are delimited text (payroll-service has no Jackson), the same format profiles use.
 */
@Getter
@Setter
@Entity
@Table(name = "payroll_salary_template")
public class SalaryTemplateEntity extends BaseEntity {

  @Column(columnDefinition = "text")
  private String components;
}
