package com.hitech.erp.config;

import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import com.hitech.erp.usermanagement.db.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds a single Super Admin user on first startup so there's always a way into the system. */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapAdminRunner implements CommandLineRunner {

  private static final String SUPER_ADMIN_ROLE = "Super Admin";

  private final AppUserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final BootstrapAdminProperties bootstrapAdminProperties;

  @Override
  @Transactional
  public void run(String... args) {
    if (userRepository.count() > 0) {
      return;
    }

    var superAdminRole =
        roleRepository
            .findByNameIgnoreCase(SUPER_ADMIN_ROLE)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "'" + SUPER_ADMIN_ROLE + "' role must be seeded by Flyway before startup"));

    AppUserEntity admin = new AppUserEntity();
    admin.setEmail(bootstrapAdminProperties.getEmail());
    admin.setFullName(bootstrapAdminProperties.getFullName());
    admin.setPasswordHash(passwordEncoder.encode(bootstrapAdminProperties.getPassword()));
    admin.setActive(true);
    admin.setRole(superAdminRole);
    userRepository.save(admin);

    log.info(
        "Seeded bootstrap admin user [{}] - change the password after first login.",
        bootstrapAdminProperties.getEmail());
  }
}
