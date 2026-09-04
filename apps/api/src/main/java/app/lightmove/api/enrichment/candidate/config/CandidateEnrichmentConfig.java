package app.lightmove.api.enrichment.candidate.config;

import app.lightmove.api.core.config.EnrichmentSettings;
import app.lightmove.api.enrichment.candidate.service.BrightDataProfileEnricher;
import app.lightmove.api.enrichment.candidate.service.FallbackProfileEnricher;
import app.lightmove.api.enrichment.candidate.service.HarvestApiProfileEnricher;
import app.lightmove.api.enrichment.candidate.service.LinkedInProfileEnricher;
import app.lightmove.api.enrichment.candidate.service.LogProfileEnricher;
import app.lightmove.api.enrichment.candidate.service.ProfilePhotoDownloader;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Picks the {@link LinkedInProfileEnricher} from config — the one place that knows which provider
 * researches captures, and whether the Bright Data dataset has a live-scrape fallback behind it.
 *
 * <p><b>Each adapter is its own {@code @Bean}, absent rather than unbuilt when its provider is off.</b>
 * {@code @Retryable} is proxy-based, and an object constructed inside another bean's factory method is
 * never proxied — building the adapters inline here would leave every retry silently inert, the same
 * way a self-invoked {@code @Async} method is. Returning {@code null} registers no bean, and
 * {@code @Autowired(required = false)} resolves that absence at the injection point below.
 *
 * <p>The adapters are {@code defaultCandidate = false} because they are themselves
 * {@link LinkedInProfileEnricher}s: left as ordinary candidates, anything injecting the port would
 * find three beans and refuse to start. Qualified by name here, they stay reachable to this class and
 * invisible to everyone else, which also leaves the test double's {@code @Primary} the only primary.
 */
@Configuration
@Slf4j
public class CandidateEnrichmentConfig {

    @Bean(defaultCandidate = false)
    BrightDataProfileEnricher brightDataProfileEnricher(LightMoveProperties properties,
                                                        VendorClientFactory clientFactory,
                                                        VendorRateLimiter rateLimiter,
                                                        VendorCallGuard guard,
                                                        ProfilePhotoDownloader photos) {
        EnrichmentSettings config = properties.enrichment();
        if (!"brightdata".equalsIgnoreCase(config.provider())) {
            return null;
        }
        requireKey(config.brightdata().apiKey(), "BRIGHTDATA_API_KEY");
        return new BrightDataProfileEnricher(config.brightdata(), clientFactory, rateLimiter, guard,
                photos, RestClient.builder());
    }

    @Bean(defaultCandidate = false)
    HarvestApiProfileEnricher harvestApiProfileEnricher(LightMoveProperties properties,
                                                        VendorClientFactory clientFactory,
                                                        VendorRateLimiter rateLimiter,
                                                        VendorCallGuard guard,
                                                        ProfilePhotoDownloader photos) {
        EnrichmentSettings config = properties.enrichment();
        boolean isPrimary = "harvestapi".equalsIgnoreCase(config.provider());
        boolean isFallback = "brightdata".equalsIgnoreCase(config.provider())
                && hasKey(config.harvestapi().apiKey());
        if (isPrimary) {
            requireKey(config.harvestapi().apiKey(), "HARVESTAPI_API_KEY");
        }
        if (!isPrimary && !isFallback) {
            return null;
        }
        return new HarvestApiProfileEnricher(config.harvestapi(), clientFactory, rateLimiter, guard,
                photos, RestClient.builder());
    }

    @Bean
    LinkedInProfileEnricher linkedInProfileEnricher(
            LightMoveProperties properties,
            @Autowired(required = false) @Qualifier("brightDataProfileEnricher")
            BrightDataProfileEnricher dataset,
            @Autowired(required = false) @Qualifier("harvestApiProfileEnricher")
            HarvestApiProfileEnricher liveScrape) {
        EnrichmentSettings config = properties.enrichment();

        return switch (config.provider().toLowerCase(Locale.ROOT)) {
            case "off", "log" -> {
                log.info("Candidate enrichment is off — captured profiles keep only what the plugin read.");
                yield new LogProfileEnricher();
            }
            case "harvestapi" -> {
                log.info("Candidate enrichment researches through HarvestAPI (live scrape)");
                yield liveScrape;
            }
            case "brightdata" -> {
                if (liveScrape == null) {
                    log.info("Candidate enrichment researches through Bright Data (no live fallback)");
                    yield dataset;
                }
                log.info("Candidate enrichment researches through Bright Data, HarvestAPI on a miss");
                yield new FallbackProfileEnricher(dataset, liveScrape);
            }
            default -> throw new IllegalStateException(
                    "Unknown lightmove.enrichment.provider '" + config.provider()
                            + "' — use off, brightdata or harvestapi");
        };
    }

    /**
     * Fail at startup rather than at the first capture, for EmailSenderConfig's reason: an instance
     * that boots happily and then silently enriches nothing is worse than one that refuses to boot.
     */
    private static void requireKey(String apiKey, String envVar) {
        if (!hasKey(apiKey)) {
            throw new IllegalStateException(
                    "lightmove.enrichment.provider is set but no API key is set (" + envVar + ")");
        }
    }

    private static boolean hasKey(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }
}
