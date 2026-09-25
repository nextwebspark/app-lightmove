package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How a plugin-captured executive is researched — {@code lightmove.enrichment.*}.
 *
 * <p>{@code off} researches nothing; {@code brightdata} answers from the stored dataset in under a
 * second and falls back to a HarvestAPI live scrape on a miss when that key is present;
 * {@code harvestapi} scrapes live every time. Off by default for the reason the email provider
 * defaults are what they are: a fresh clone must run with zero vendor accounts, and every
 * enrichment call is billed.
 *
 * <p>{@code contactout} is <b>not</b> selected by {@code provider}. Contact lookup is a separate
 * capability with its own key and its own bill, so {@code provider: off} must leave the drawer's
 * Find email / Find phone buttons working.
 *
 * <p>{@code backgroundInference} is the model call that follows whatever research {@code provider}
 * fetched; off keeps the research and skips the call.
 */
public record EnrichmentSettings(
        @DefaultValue("off") String provider,
        BrightDataSettings brightdata,
        HarvestApiSettings harvestapi,
        ContactOutSettings contactout,
        @DefaultValue("true") boolean backgroundInference,

        /**
         * How long a row of {@code app_lm_vendor_company} answers for a slug before the provider is asked
         * again. Mapbox's terms are why {@code lightmove.mapbox.cache-ttl} exists; Bright Data's own
         * terms are the thing to check before raising this, not this default.
         */
        @DefaultValue("30d") Duration companyCacheTtl
) {}
