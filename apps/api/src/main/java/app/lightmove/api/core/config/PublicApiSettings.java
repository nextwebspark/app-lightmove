package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The public API and its keys — {@code lightmove.public-api.*}. Every key expires; none outlives {@code maxKeyTtl}. */
public record PublicApiSettings(
        /** Off answers 404 for everything under {@code /api/v1/public}, the docs included. */
        @DefaultValue("true") boolean enabled,
        /** Per key, across every route: a greedy bucket, so a burst spends it and it drips back. */
        @DefaultValue("60") int requestsPerMinute,
        @DefaultValue("90d") Duration defaultKeyTtl,
        @DefaultValue("365d") Duration maxKeyTtl,
        @DefaultValue("10") int maxActiveKeysPerUser
) {}
