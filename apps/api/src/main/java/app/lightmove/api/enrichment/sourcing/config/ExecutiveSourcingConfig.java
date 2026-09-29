package app.lightmove.api.enrichment.sourcing.config;

import app.lightmove.api.core.config.EnrichmentSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.enrichment.sourcing.service.BrightDataPeopleSearch;
import app.lightmove.api.enrichment.sourcing.service.LogPeopleSearch;
import app.lightmove.api.enrichment.sourcing.service.PeopleSearch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Picks the {@link PeopleSearch}: Bright Data's when the enrichment provider is Bright Data (the same
 * key and dataset), the no-vendor stand-in otherwise. The adapter is its own {@code @Bean} for
 * {@code CandidateEnrichmentConfig}'s reason — built inside another factory method its
 * {@code @Retryable} would never fire.
 */
@Configuration
@Slf4j
public class ExecutiveSourcingConfig {

    @Bean(defaultCandidate = false)
    BrightDataPeopleSearch brightDataPeopleSearch(LightMoveProperties properties, VendorClientFactory clientFactory,
                                                  VendorRateLimiter rateLimiter, VendorCallGuard guard,
                                                  ObjectMapper json) {
        EnrichmentSettings config = properties.enrichment();
        if (!"brightdata".equalsIgnoreCase(config.provider())) {
            return null;
        }
        return new BrightDataPeopleSearch(config.brightdata(), clientFactory, rateLimiter, guard, RestClient.builder(),
                json);
    }

    @Bean
    PeopleSearch peopleSearch(@Autowired(required = false) @Qualifier("brightDataPeopleSearch")
                              BrightDataPeopleSearch dataset) {
        if (dataset == null) {
            log.info("Find executives is off — no people-search provider configured.");
            return new LogPeopleSearch();
        }
        log.info("Find executives searches Bright Data's people dataset");
        return dataset;
    }
}
