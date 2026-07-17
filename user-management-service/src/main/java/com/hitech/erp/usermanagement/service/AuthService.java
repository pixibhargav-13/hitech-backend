package com.hitech.erp.usermanagement.service;

import com.hitech.erp.api.usermanagement.model.AuthResponse;
import com.hitech.erp.api.usermanagement.model.CurrentUserResponse;
import com.hitech.erp.api.usermanagement.model.RoleSummary;
import com.hitech.erp.common.exception.InvalidCredentialsException;
import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import com.hitech.erp.usermanagement.db.PermissionEntity;
import com.hitech.erp.usermanagement.db.RefreshTokenEntity;
import com.hitech.erp.usermanagement.db.RefreshTokenRepository;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import com.hitech.erp.usermanagement.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final AppUserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Transactional
  public AuthResponse login(String email, String password) {
    AppUserEntity user =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

    if (!user.isActive() || !passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new InvalidCredentialsException("Invalid email or password");
    }

    return issueTokens(user);
  }

  @Transactional
  public AuthResponse refresh(String rawRefreshToken) {
    RefreshTokenEntity existing = requireValidRefreshToken(rawRefreshToken);
    existing.setRevoked(true);
    refreshTokenRepository.save(existing);

    AppUserEntity user = existing.getUser();
    if (!user.isActive()) {
      throw new InvalidCredentialsException("User is inactive");
    }
    return issueTokens(user);
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    String hash = jwtTokenProvider.hashToken(rawRefreshToken);
    refreshTokenRepository
        .findByTokenHashAndRevokedFalse(hash)
        .ifPresent(
            token -> {
              token.setRevoked(true);
              refreshTokenRepository.save(token);
            });
  }

  public CurrentUserResponse getCurrentUser(AuthenticatedUser principal) {
    CurrentUserResponse response = new CurrentUserResponse();
    response.setId(principal.id());
    response.setEmail(principal.email());
    response.setFullName(principal.fullName());
    response.setRole(new RoleSummary().id(principal.roleId()).name(principal.roleName()));
    response.setPermissions(principal.permissions());
    return response;
  }

  private RefreshTokenEntity requireValidRefreshToken(String rawRefreshToken) {
    String hash = jwtTokenProvider.hashToken(rawRefreshToken);
    RefreshTokenEntity token =
        refreshTokenRepository
            .findByTokenHashAndRevokedFalse(hash)
            .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

    if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
      throw new InvalidCredentialsException("Refresh token expired");
    }
    return token;
  }

  private AuthResponse issueTokens(AppUserEntity user) {
    List<String> permissionCodes =
        user.getRole().getPermissions().stream().map(PermissionEntity::getCode).sorted().toList();

    String accessToken = jwtTokenProvider.generateAccessToken(user, permissionCodes);
    String refreshToken = jwtTokenProvider.generateRefreshTokenValue();

    RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
    refreshTokenEntity.setUser(user);
    refreshTokenEntity.setTokenHash(jwtTokenProvider.hashToken(refreshToken));
    refreshTokenEntity.setExpiresAt(
        LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshTokenTtlSeconds()));
    refreshTokenRepository.save(refreshTokenEntity);

    CurrentUserResponse userResponse = new CurrentUserResponse();
    userResponse.setId(user.getId());
    userResponse.setEmail(user.getEmail());
    userResponse.setFullName(user.getFullName());
    userResponse.setRole(new RoleSummary().id(user.getRole().getId()).name(user.getRole().getName()));
    userResponse.setPermissions(permissionCodes);

    return new AuthResponse()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .expiresIn(jwtTokenProvider.getAccessTokenTtlSeconds())
        .user(userResponse);
  }
}
