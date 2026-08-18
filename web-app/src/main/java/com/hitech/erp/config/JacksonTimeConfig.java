package com.hitech.erp.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stamps the India offset onto every {@code LocalDateTime} we serialise.
 *
 * <p>By default Jackson writes a {@code LocalDateTime} with no timezone designator at all —
 * {@code "2026-08-10T12:26:57.416257"}. JavaScript reads such a string as the *browser's* local
 * time, so the client had to guess which zone the value came from, and guessed wrong whenever the
 * browser and the server disagreed. Emitting {@code "2026-08-10T12:26:57.416+05:30"} makes the
 * instant explicit, so `new Date(...)` lands on the right moment everywhere.
 *
 * <p>Read together with the timezone pin in {@code HitechErpApplication}: that guarantees the
 * stored wall clock really is IST, which is what lets us attach this offset.
 */
@Configuration
public class JacksonTimeConfig {

  public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  @Bean
  public SimpleModule istDateTimeModule() {
    SimpleModule module = new SimpleModule("ist-datetime");
    module.addSerializer(LocalDateTime.class, new IstLocalDateTimeSerializer());
    return module;
  }

  private static final class IstLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      // Postgres keeps microseconds; browsers only reliably parse three fraction digits.
      gen.writeString(
          value.truncatedTo(ChronoUnit.MILLIS).atZone(IST).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
  }
}
