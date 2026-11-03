package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bright Data's Business Search as Find executives' profile index —
 * {@code lightmove.enrichment.sourcing.business-search.*}. It answers on the Bright Data key, whatever
 * {@code enrichment.provider} says; with no key it is not offered.
 */
public record BusinessSearchSettings(

        @DefaultValue("true") boolean enabled,

        /** Calls fired in parallel got the address blocked for half an hour; one every 1.5s ran clean. */
        @DefaultValue("1500ms") Duration minInterval,

        /** How long a run's call queues for its turn behind the others before it gives up. */
        @DefaultValue("60s") Duration permitWait,

        @DefaultValue("20s") Duration readTimeout
) {

    public BusinessSearchSettings {
        if (minInterval == null || minInterval.isNegative() || minInterval.isZero()) {
            throw new IllegalArgumentException(
                    "lightmove.enrichment.sourcing.business-search.min-interval must be positive");
        }
    }
}
