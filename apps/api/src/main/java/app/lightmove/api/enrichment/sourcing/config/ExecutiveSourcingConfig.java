package app.lightmove.api.enrichment.sourcing.config;

import app.lightmove.api.core.config.ContactOutSettings;
import app.lightmove.api.core.config.EnrichmentSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.enrichment.sourcing.service.BrightDataPeopleSearch;
import app.lightmove.api.enrichment.sourcing.service.ContactOutPeopleSearch;
import app.lightmove.api.enrichment.sourcing.service.LogPeopleSearch;
import app.lightmove.api.enrichment.sourcing.service.PeopleSearch;
import app.lightmove.api.enrichment.sourcing.service.PeopleSearchChain;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Picks the {@link PeopleSearch} {@code sourcing.people-source} names: ContactOut's People Search when
 * a ContactOut key is set, or Bright Data's dataset when the enrichment provider is Bright Data (the
 * same key and dataset); the no-vendor stand-in when neither is configured. With ContactOut first and
 * Bright Data configured, the {@link PeopleSearchChain} asks Bright Data wherever ContactOut finds
 * nobody or fails. Each adapter is its own {@code @Bean} for {@code CandidateEnrichmentConfig}'s
 * reason — built inside another factory method its {@code @Retryable} would never fire.
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

    @Bean(defaultCandidate = false)
    ContactOutPeopleSearch contactOutPeopleSearch(LightMoveProperties properties, VendorClientFactory clientFactory,
                                                  VendorRateLimiter rateLimiter, VendorCallGuard guard,
                                                  ObjectMapper json) {
        EnrichmentSettings config = properties.enrichment();
        ContactOutSettings contactOut = config.contactout();
        if (!config.sourcing().searchesContactOut() || contactOut == null || !contactOut.isConfigured()) {
            return null;
        }
        return new ContactOutPeopleSearch(contactOut, clientFactory, rateLimiter, guard, RestClient.builder(), json,
                config.sourcing().picksPerCompany());
    }

    @Bean
    PeopleSearch peopleSearch(@Autowired(required = false) @Qualifier("brightDataPeopleSearch")
                              BrightDataPeopleSearch dataset,
                              @Autowired(required = false) @Qualifier("contactOutPeopleSearch")
                              ContactOutPeopleSearch contactOut) {
        if (contactOut != null) {
            log.info("Find executives searches ContactOut's people index{}",
                    dataset == null ? "" : ", then Bright Data's dataset where it finds nobody or fails");
            return contactOut;
        }
        if (dataset == null) {
            log.info("Find executives is off — no people-search provider configured.");
            return new LogPeopleSearch();
        }
        log.info("Find executives searches Bright Data's people dataset");
        return dataset;
    }

    /** The primary {@code PeopleSearch}, then Bright Data's dataset behind it when that is a different search. */
    @Bean
    PeopleSearchChain peopleSearchChain(PeopleSearch primary,
                                        @Autowired(required = false) @Qualifier("brightDataPeopleSearch")
                                        BrightDataPeopleSearch dataset) {
        if (dataset == null || dataset == primary) {
            return new PeopleSearchChain(List.of(primary));
        }
        return new PeopleSearchChain(List.of(primary, dataset));
    }
}
