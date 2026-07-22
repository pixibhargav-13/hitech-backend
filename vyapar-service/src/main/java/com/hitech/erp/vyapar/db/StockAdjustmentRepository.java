package com.hitech.erp.vyapar.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustmentEntity, Long> {
  List<StockAdjustmentEntity> findAllByItemIdOrderByIdDesc(Long itemId);
}
