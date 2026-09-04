package app.lightmove.api.enrichment.company.config;

import app.lightmove.api.core.config.EnrichmentSettings;
import app.lightmove.api.enrichment.company.service.BrightDataCompanyEnricher;
import app.lightmove.api.enrichment.company.service.LinkedInCompanyEnricher;
import app.lightmove.api.enrichment.company.service.LogCompanyEnricher;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Picks the {@link LinkedInCompanyEnricher} from config. Company research rides the Bright Data key
 * the person enrichment already requires — there is no provider knob of its own, because a second
 * knob would only invite the two halves of one capture to disagree.
 *
 * <p>The adapter is its own {@code @Bean}, and {@code defaultCandidate = false}, for the two reasons
 * {@code CandidateEnrichmentConfig} explains at length: an adapter constructed inline inside another
 * factory method is never proxied and its {@code @Retryable} would be inert, and an adapter left as an
 * ordinary candidate would make injecting the port ambiguous.
 */
@Configuration
@Slf4j
public class CompanyEnrichmentConfig {

    @Bean(defaultCandidate = false)
    BrightDataCompanyEnricher brightDataCompanyEnricher(LightMoveProperties properties,
                                                        VendorClientFactory clientFactory,
                                                        VendorRateLimiter rateLimiter,
                                                        VendorCallGuard guard) {
        EnrichmentSettings config = properties.enrichment();
        String apiKey = config.brightdata().apiKey();
        if (!"brightdata".equalsIgnoreCase(config.provider()) || apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return new BrightDataCompanyEnricher(config.brightdata(), clientFactory, rateLimiter, guard,
                RestClient.builder());
    }

    @Bean
    LinkedInCompanyEnricher linkedInCompanyEnricher(
            @Autowired(required = false) @Qualifier("brightDataCompanyEnricher")
            BrightDataCompanyEnricher dataset) {
        if (dataset == null) {
            log.info("Company enrichment is off — captured companies keep what the plugin read.");
            return new LogCompanyEnricher();
        }
        log.info("Company enrichment researches through Bright Data's company dataset");
        return dataset;
    }
}
