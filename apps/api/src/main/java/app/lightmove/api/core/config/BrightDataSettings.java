package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credentials and datasets for the Bright Data Marketplace lookups —
 * {@code lightmove.enrichment.brightdata.*}. The dataset ids name Bright Data's LinkedIn people and
 * company datasets, two of the few their sync Search API serves.
 */
public record BrightDataSettings(
        String apiKey,
        @DefaultValue("https://api.brightdata.com") String baseUrl,
        @DefaultValue("gd_l1viktl72bvl7bjuj0") String datasetId,
        @DefaultValue("gd_l1vikfnt1wgvvqz95w") String companyDatasetId,

        /** No cap is published, so calls are paced conservatively rather than discovered as 429s. */
        @DefaultValue("10") int requestsPerSecond
) {}
