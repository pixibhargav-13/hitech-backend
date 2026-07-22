package com.hitech.erp.vyapar.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
  List<PaymentEntity> findAllByOrderByIdDesc();

  List<PaymentEntity> findAllByDirectionOrderByIdDesc(String direction);
}
