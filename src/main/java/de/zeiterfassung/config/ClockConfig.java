package de.zeiterfassung.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Zentrale Zeitquelle. In Tests wird sie durch eine feste Uhr ersetzt. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
