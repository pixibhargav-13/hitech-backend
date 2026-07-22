package com.hitech.erp.vyapar.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<ItemEntity, Long> {
  List<ItemEntity> findAllByOrderByNameAsc();
}
