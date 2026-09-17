package app.lightmove.api.core.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A clock to inject rather than read off {@code Instant.now()}, so a calculation that depends on
 * today — a mandate's week count, a projection — can be tested against a fixed date. The report is
 * its only reader so far; the rest of the application still asks the system clock directly.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
