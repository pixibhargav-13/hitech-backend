package com.hitech.erp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public class BootstrapAdminProperties {

  private String email = "admin@hitech.local";

  private String password = "Admin@123";

  private String fullName = "Super Admin";
}
