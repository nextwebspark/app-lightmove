package app.lightmove.api.core.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one clock the application tells time by. Injected rather than read off {@code Instant.now()}
 * so a calculation that depends on today — a mandate's week count, a projection — can be tested
 * against a fixed date.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
