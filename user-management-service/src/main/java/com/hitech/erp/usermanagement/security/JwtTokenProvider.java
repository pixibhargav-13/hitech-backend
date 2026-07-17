package com.hitech.erp.usermanagement.security;

import com.hitech.erp.usermanagement.db.AppUserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_FULL_NAME = "fullName";
  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_ROLE_ID = "roleId";
  private static final String CLAIM_PERMISSIONS = "permissions";

  private final JwtProperties jwtProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  private SecretKey signingKey() {
    return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
  }

  public long getAccessTokenTtlSeconds() {
    return jwtProperties.getAccessTokenTtlMinutes() * 60;
  }

  public String generateAccessToken(AppUserEntity user, List<String> permissionCodes) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(user.getId()))
        .claim(CLAIM_EMAIL, user.getEmail())
        .claim(CLAIM_FULL_NAME, user.getFullName())
        .claim(CLAIM_ROLE, user.getRole().getName())
        .claim(CLAIM_ROLE_ID, user.getRole().getId())
        .claim(CLAIM_PERMISSIONS, permissionCodes)
        .issuedAt(java.util.Date.from(now))
        .expiration(java.util.Date.from(now.plusSeconds(getAccessTokenTtlSeconds())))
        .signWith(signingKey())
        .compact();
  }

  /**
   * Parses and validates an access token.
   *
   * @throws JwtException if the token is malformed, expired, or has an invalid signature
   */
  public AuthenticatedUser parseAccessToken(String token) {
    Claims claims = Jwts.parser().verifyWith(signingKey()).build()
        .parseSignedClaims(token)
        .getPayload();

    @SuppressWarnings("unchecked")
    List<String> permissions = claims.get(CLAIM_PERMISSIONS, List.class);

    return new AuthenticatedUser(
        Long.valueOf(claims.getSubject()),
        claims.get(CLAIM_EMAIL, String.class),
        claims.get(CLAIM_FULL_NAME, String.class),
        claims.get(CLAIM_ROLE_ID, Long.class),
        claims.get(CLAIM_ROLE, String.class),
        permissions);
  }

  /** Generates a new opaque, high-entropy refresh token (the raw value returned to the client). */
  public String generateRefreshTokenValue() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public long getRefreshTokenTtlSeconds() {
    return jwtProperties.getRefreshTokenTtlDays() * 24 * 60 * 60;
  }

  /** Refresh tokens are stored hashed so a DB leak alone can't be replayed. */
  public String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
