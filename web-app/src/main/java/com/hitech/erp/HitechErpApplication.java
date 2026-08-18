package com.hitech.erp;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class HitechErpApplication {

  /**
   * The business runs entirely from India, so the JVM clock is pinned to IST before anything else
   * starts.
   *
   * <p>Every timestamp in the system is a {@code LocalDateTime} — {@code BaseEntity.createdAt},
   * task chat/activity {@code at}, audit rows, payroll punch times — and {@code LocalDateTime.now()}
   * takes its wall clock from the JVM's default zone. That zone was whatever the host happened to
   * be: IST on a developer's Windows box, but UTC inside the {@code eclipse-temurin} container. The
   * same code therefore wrote 12:26 in one environment and 06:56 in another for the same moment,
   * and the UI showed task chat and activity 5½ hours behind real Indian time.
   *
   * <p>Setting it here rather than in {@code application.yaml} means Flyway, Hibernate and the JPA
   * auditing listener all come up already on IST.
   */
  static {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
  }

  public static void main(String[] args) {
    SpringApplication.run(HitechErpApplication.class, args);
  }
}
