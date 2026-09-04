package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How this application calls a paid third-party API — {@code lightmove.resilience.*}.
 *
 * <p>The retry knobs are read by {@code @Retryable} through {@code ${...}} placeholders rather than
 * injected, which is what lets a test profile run the same code paths at millisecond delays.
 */
public record ResilienceSettings(
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("2") int maxRetries,
        @DefaultValue("500ms") Duration retryDelay,
        @DefaultValue("2.0") double retryMultiplier,

        /** Randomises the delay so callers throttled together do not return together. */
        @DefaultValue("250ms") Duration retryJitter,
        @DefaultValue("5s") Duration retryMaxDelay,

        /**
         * The longest a call waits for its own rate-limit permit before failing as rate limited.
         * Bounded on purpose: an unbounded wait parks callers long after whoever asked gave up.
         */
        @DefaultValue("2s") Duration permitMaxWait
) {}
