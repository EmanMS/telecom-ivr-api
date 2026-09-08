package com.vodafone.ivr;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application-wide infrastructure beans.
 */
@Configuration
public class ApplicationConfig {

    /**
     * Exposes the system clock as a bean so that time-dependent business rules (card expiry) can be
     * unit-tested deterministically by injecting a fixed clock instead of calling
     * {@code YearMonth.now()} directly inside the service.
     */
    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
