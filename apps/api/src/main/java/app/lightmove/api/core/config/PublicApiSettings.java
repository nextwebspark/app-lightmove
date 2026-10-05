package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The public API and its keys — {@code lightmove.public-api.*}. Every key expires; none outlives {@code maxKeyTtl}. */
public record PublicApiSettings(
        @DefaultValue("90d") Duration defaultKeyTtl,
        @DefaultValue("365d") Duration maxKeyTtl,
        @DefaultValue("10") int maxActiveKeysPerUser
) {}
