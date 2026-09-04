package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Credentials for the HarvestAPI LinkedIn data provider — {@code lightmove.enrichment.harvestapi.*}. */
public record HarvestApiSettings(
        String apiKey,
        @DefaultValue("https://api.harvestapi.io") String baseUrl,

        /** No cap is published, so calls are paced conservatively rather than discovered as 429s. */
        @DefaultValue("10") int requestsPerSecond
) {}
