package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Per-IP budgets for the abuse-prone auth endpoints — {@code lightmove.auth.rate-limit.*}. */
public record RateLimitSettings(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") int loginAttemptsPerMinute,
        @DefaultValue("5") int signupAttemptsPerHour,
        @DefaultValue("3") int verificationResendsPerHour,
        @DefaultValue("3") int passwordResetRequestsPerHour,
        @DefaultValue("10") int passwordChangeAttemptsPerHour,

        /**
         * Pairing the browser extension. Low because pairing is a rare deliberate act — a consultant
         * does it once per machine — while the thing it mints is a long-lived credential that leaves
         * in a response body. A script that has got hold of the in-memory access token should not be
         * able to farm tokens with it in a loop.
         */
        @DefaultValue("5") int extensionPairingsPerHour
) {}
