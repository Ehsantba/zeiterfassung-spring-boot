package de.zeiterfassung;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Feste Uhr für reproduzierbare Tests: Mittwoch, 18.06.2025, 12:00 Uhr Berliner Zeit. */
@TestConfiguration
class TestClockConfig {
    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(Instant.parse("2025-06-18T10:00:00Z"), ZoneOffset.UTC);
    }
}
