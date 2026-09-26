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
import tools.jackson.databind.ObjectMapper;

/**
 * Picks the {@link LinkedInCompanyEnricher}. It rides the person enrichment's Bright Data key — no
 * knob of its own, so one capture's two halves cannot disagree. Bean shape as
 * {@code CandidateEnrichmentConfig}, for its reasons.
 */
@Configuration
@Slf4j
public class CompanyEnrichmentConfig {

    @Bean(defaultCandidate = false)
    BrightDataCompanyEnricher brightDataCompanyEnricher(LightMoveProperties properties,
                                                        VendorClientFactory clientFactory,
                                                        VendorRateLimiter rateLimiter,
                                                        VendorCallGuard guard,
                                                        ObjectMapper json) {
        EnrichmentSettings config = properties.enrichment();
        String apiKey = config.brightdata().apiKey();
        if (!"brightdata".equalsIgnoreCase(config.provider()) || apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return new BrightDataCompanyEnricher(config.brightdata(), clientFactory, rateLimiter, guard,
                RestClient.builder(), json);
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
