package com.hitech.erp.usermanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.RoleEntity;
import io.jsonwebtoken.JwtException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret("test-signing-secret-test-signing-secret-32b");
    properties.setAccessTokenTtlMinutes(30);
    properties.setRefreshTokenTtlDays(7);

    jwtTokenProvider = new JwtTokenProvider(properties);
  }

  private AppUserEntity buildUser() {
    RoleEntity role = new RoleEntity();
    role.setName("Super Admin");

    AppUserEntity user = new AppUserEntity();
    user.setId(10L);
    user.setEmail("admin@hitech.local");
    user.setFullName("Super Admin");
    user.setRole(role);
    return user;
  }

  @Test
  void generatesAndParsesAccessTokenRoundTrip() {
    AppUserEntity user = buildUser();
    List<String> permissions = List.of("PROJECT:VIEW", "PROJECT:EDIT");

    String token = jwtTokenProvider.generateAccessToken(user, permissions);
    AuthenticatedUser parsed = jwtTokenProvider.parseAccessToken(token);

    assertThat(parsed.email()).isEqualTo("admin@hitech.local");
    assertThat(parsed.fullName()).isEqualTo("Super Admin");
    assertThat(parsed.roleName()).isEqualTo("Super Admin");
    assertThat(parsed.permissions()).containsExactlyInAnyOrder("PROJECT:VIEW", "PROJECT:EDIT");
  }

  @Test
  void rejectsTokenSignedWithDifferentSecret() {
    JwtProperties otherProperties = new JwtProperties();
    otherProperties.setSecret("a-completely-different-signing-secret-32b");
    otherProperties.setAccessTokenTtlMinutes(30);
    JwtTokenProvider otherProvider = new JwtTokenProvider(otherProperties);

    String token = otherProvider.generateAccessToken(buildUser(), List.of("PROJECT:VIEW"));

    assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void hashTokenIsDeterministicAndDiffersPerInput() {
    String hashA = jwtTokenProvider.hashToken("raw-refresh-token-a");
    String hashB = jwtTokenProvider.hashToken("raw-refresh-token-a");
    String hashC = jwtTokenProvider.hashToken("raw-refresh-token-c");

    assertThat(hashA).isEqualTo(hashB);
    assertThat(hashA).isNotEqualTo(hashC);
  }
}
