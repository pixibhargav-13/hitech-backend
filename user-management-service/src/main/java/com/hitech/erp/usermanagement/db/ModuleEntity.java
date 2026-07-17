package com.hitech.erp.usermanagement.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "modules")
public class ModuleEntity extends BaseEntity {

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<PermissionEntity> permissions = new ArrayList<>();
}
