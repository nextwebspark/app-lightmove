package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How a plugin-captured executive is researched — {@code lightmove.enrichment.*}.
 *
 * <p>{@code off} researches nothing; {@code brightdata} answers from the stored dataset in under a
 * second and falls back to a HarvestAPI live scrape on a miss when that key is present;
 * {@code harvestapi} scrapes live every time. Off by default for the reason the email provider
 * defaults are what they are: a fresh clone must run with zero vendor accounts, and every
 * enrichment call is billed.
 */
public record EnrichmentSettings(
        @DefaultValue("off") String provider,
        BrightDataSettings brightdata,
        HarvestApiSettings harvestapi
) {}
