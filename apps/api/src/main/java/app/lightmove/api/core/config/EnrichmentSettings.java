package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How a captured executive is researched — {@code lightmove.enrichment.*}; off by default, as every
 * call is billed. {@code contactout} is <b>not</b> selected by {@code provider}: contact lookup has its
 * own bill, so {@code provider: off} leaves Find email / Find phone working.
 */
public record EnrichmentSettings(
        @DefaultValue("off") String provider,
        BrightDataSettings brightdata,
        HarvestApiSettings harvestapi,
        ContactOutSettings contactout,
        @DefaultValue("true") boolean aiEnrichOnCapture,

        /**
         * How long a row of {@code app_lm_vendor_company} answers for a slug before the provider is asked
         * again. Mapbox's terms are why {@code lightmove.mapbox.cache-ttl} exists; Bright Data's own
         * terms are the thing to check before raising this, not this default.
         */
        @DefaultValue("30d") Duration companyCacheTtl
) {}
