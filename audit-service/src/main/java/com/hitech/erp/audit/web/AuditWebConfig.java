package com.hitech.erp.audit.web;

import com.hitech.erp.audit.service.AuditRecorder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditWebConfig {

  /**
   * Order 0 places this after Spring Security's chain (registered at -100), so the JWT
   * principal is resolved by the time the filter records the event.
   */
  @Bean
  public FilterRegistrationBean<AuditLoggingFilter> auditLoggingFilter(AuditRecorder recorder) {
    FilterRegistrationBean<AuditLoggingFilter> registration =
        new FilterRegistrationBean<>(new AuditLoggingFilter(recorder));
    registration.addUrlPatterns("/api/*");
    registration.setOrder(0);
    return registration;
  }
}
