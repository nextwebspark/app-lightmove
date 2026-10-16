package app.uncava.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Failed-login lockout thresholds — {@code uncava.auth.lockout.*}. */
public record LockoutSettings(
        @DefaultValue("5") int maxFailedAttempts,
        @DefaultValue("15m") Duration duration
) {}
