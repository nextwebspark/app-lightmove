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
        @DefaultValue("5") int extensionPairingsPerHour,

        /**
         * The same action's budget per source IP, and deliberately far looser than the per-account one.
         * A firm shares an office IP; the threat this guards against does not. Tightening this to match
         * the account figure rate-limits a whole team out of a first-run setup step to defend against
         * nothing the account budget does not already cover.
         */
        @DefaultValue("60") int extensionPairingsPerHourPerIp
) {}
