package com.hitech.erp.usermanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hitech.erp.api.usermanagement.model.AuthResponse;
import com.hitech.erp.common.exception.InvalidCredentialsException;
import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import com.hitech.erp.usermanagement.db.RefreshTokenRepository;
import com.hitech.erp.usermanagement.db.RoleEntity;
import com.hitech.erp.usermanagement.security.JwtTokenProvider;
import java.util.HashSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private AppUserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenProvider jwtTokenProvider;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtTokenProvider);
  }

  private AppUserEntity activeUser() {
    RoleEntity role = new RoleEntity();
    role.setId(1L);
    role.setName("Super Admin");
    role.setPermissions(new HashSet<>());

    AppUserEntity user = new AppUserEntity();
    user.setId(10L);
    user.setEmail("admin@hitech.local");
    user.setFullName("Super Admin");
    user.setPasswordHash("hashed");
    user.setActive(true);
    user.setRole(role);
    return user;
  }

  @Test
  void loginSucceedsAndIssuesTokens() {
    AppUserEntity user = activeUser();
    when(userRepository.findByEmailIgnoreCase("admin@hitech.local")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("correct-password", "hashed")).thenReturn(true);
    when(jwtTokenProvider.generateAccessToken(any(), any())).thenReturn("access-token");
    when(jwtTokenProvider.generateRefreshTokenValue()).thenReturn("raw-refresh-token");
    when(jwtTokenProvider.hashToken("raw-refresh-token")).thenReturn("hashed-refresh-token");
    when(jwtTokenProvider.getRefreshTokenTtlSeconds()).thenReturn(604800L);
    when(jwtTokenProvider.getAccessTokenTtlSeconds()).thenReturn(1800L);

    AuthResponse response = authService.login("admin@hitech.local", "correct-password");

    assertThat(response.getAccessToken()).isEqualTo("access-token");
    assertThat(response.getRefreshToken()).isEqualTo("raw-refresh-token");
    assertThat(response.getUser().getEmail()).isEqualTo("admin@hitech.local");
  }

  @Test
  void loginFailsWithWrongPassword() {
    AppUserEntity user = activeUser();
    when(userRepository.findByEmailIgnoreCase("admin@hitech.local")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

    assertThatThrownBy(() -> authService.login("admin@hitech.local", "wrong-password"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void loginFailsForUnknownEmail() {
    when(userRepository.findByEmailIgnoreCase("nobody@hitech.local")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login("nobody@hitech.local", "whatever"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void loginFailsForInactiveUser() {
    AppUserEntity user = activeUser();
    user.setActive(false);
    when(userRepository.findByEmailIgnoreCase("admin@hitech.local")).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> authService.login("admin@hitech.local", "correct-password"))
        .isInstanceOf(InvalidCredentialsException.class);
  }
}
