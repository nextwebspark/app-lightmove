package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Credentials for the HarvestAPI LinkedIn data provider — {@code lightmove.enrichment.harvestapi.*}. */
public record HarvestApiSettings(
        String apiKey,
        @DefaultValue("https://api.harvestapi.io") String baseUrl,

        /** A live scrape takes seconds — but never a request thread. */
        @DefaultValue("60s") Duration readTimeout,

        /** No cap is published, so calls are paced conservatively rather than discovered as 429s. */
        @DefaultValue("10") int requestsPerSecond
) {}
