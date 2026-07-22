package com.hitech.erp.vyapar.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartyRepository extends JpaRepository<PartyEntity, Long> {
  List<PartyEntity> findAllByOrderByNameAsc();

  List<PartyEntity> findAllByPartyTypeOrderByNameAsc(String partyType);
}
