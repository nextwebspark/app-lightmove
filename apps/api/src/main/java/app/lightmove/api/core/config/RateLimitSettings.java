package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Per-IP budgets for the abuse-prone auth endpoints — {@code lightmove.auth.rate-limit.*}. */
public record RateLimitSettings(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") int loginAttemptsPerMinute,
        @DefaultValue("5") int signupAttemptsPerHour,
        @DefaultValue("3") int verificationResendsPerHour,
        @DefaultValue("3") int passwordResetRequestsPerHour
) {}
