package com.hitech.erp.usermanagement.db;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

  Optional<RefreshTokenEntity> findByTokenHashAndRevokedFalse(String tokenHash);

  void deleteByUserId(Long userId);
}
