package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How this application talks to paid third-party APIs — {@code lightmove.vendor.*}.
 *
 * <p>These are the shared defaults. A {@code VendorClientSpec} may override the timeouts per vendor,
 * because the differences between vendors are exactly the interesting ones: a paginated people search
 * is not a mail API and should not be held to the same read timeout.
 *
 * <p>The retry knobs are read by {@code @Retryable} through {@code ${...}} placeholders rather than
 * injected, which is what lets the test profile run the same code paths with millisecond delays.
 */
public record VendorSettings(
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("15s") Duration readTimeout,

        /** Retries <i>after</i> the first attempt, so 2 means at most three calls. */
        @DefaultValue("2") int maxRetries,
        @DefaultValue("500ms") Duration retryDelay,
        @DefaultValue("2.0") double retryMultiplier,
        /** Randomises the delay so parallel callers that were throttled together do not return together. */
        @DefaultValue("250ms") Duration retryJitter,
        @DefaultValue("5s") Duration retryMaxDelay,

        /**
         * How long a {@code Retry-After} may ask us to wait before we stop rather than retry.
         *
         * <p>Spring's backoff cannot read the header (see {@code VendorFailure#worthRetrying}), so
         * the choice is between ignoring what the vendor asked and honouring it as a decision. Past
         * this, a request thread parked waiting is worth less than a fast, honest failure.
         */
        @DefaultValue("2s") Duration retryAfterCeiling,

        /**
         * The longest a call will wait for its own rate-limit permit before failing as rate limited.
         *
         * <p>Bounded on purpose. Virtual threads mean no pool is applying backpressure, so an
         * unbounded wait lets a burst queue thousands of callers behind a slow bucket, each parked
         * long after whoever asked has given up.
         */
        @DefaultValue("2s") Duration permitMaxWait,

        CoresignalSettings coresignal
) {}
