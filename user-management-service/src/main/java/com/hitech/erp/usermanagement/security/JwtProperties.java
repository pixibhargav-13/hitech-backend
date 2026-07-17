package com.hitech.erp.usermanagement.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

  /** HMAC-SHA256 signing secret. Must be at least 32 bytes. */
  private String secret;

  private long accessTokenTtlMinutes = 30;

  private long refreshTokenTtlDays = 7;
}
