package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The public API and its keys — {@code lightmove.public-api.*}. Every key expires; none outlives {@code maxKeyTtl}. */
public record PublicApiSettings(
        /** Per key, across every route. Counted per instance, so N instances allow N times this. */
        @DefaultValue("60") int requestsPerMinute,
        /** Per caller IP, every attempt counted, a refused one included: the brake on guessing keys. */
        @DefaultValue("300") int requestsPerMinutePerIp,
        @DefaultValue("90d") Duration defaultKeyTtl,
        @DefaultValue("365d") Duration maxKeyTtl,
        @DefaultValue("10") int maxActiveKeysPerUser
) {}
