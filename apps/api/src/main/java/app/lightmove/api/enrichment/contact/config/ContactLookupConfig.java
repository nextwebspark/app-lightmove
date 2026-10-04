package app.lightmove.api.enrichment.contact.config;

import app.lightmove.api.core.config.ContactOutSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.enrichment.contact.service.ContactFinder;
import app.lightmove.api.enrichment.contact.service.ContactOutContactFinder;
import app.lightmove.api.enrichment.contact.service.LogContactFinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Picks the {@link ContactFinder}, shaped like {@code CandidateEnrichmentConfig} for its reasons. A
 * missing key does not fail the boot: it is simply a deployment without a ContactOut account.
 */
@Configuration
@Slf4j
public class ContactLookupConfig {

    @Bean(defaultCandidate = false)
    ContactOutContactFinder contactOutContactFinder(LightMoveProperties properties,
                                                    VendorClientFactory clientFactory,
                                                    VendorRateLimiter rateLimiter, VendorCallGuard guard) {
        ContactOutSettings config = properties.enrichment().contactout();
        if (config == null || !config.isConfigured()) {
            return null;
        }
        return new ContactOutContactFinder(config, clientFactory, rateLimiter, guard, RestClient.builder());
    }

    @Bean
    ContactFinder contactFinder(@Autowired(required = false) @Qualifier("contactOutContactFinder")
                                ContactOutContactFinder contactOut) {
        if (contactOut == null) {
            log.info("Contact lookup is off — no ContactOut key configured, so the buttons are not offered.");
            return new LogContactFinder();
        }
        log.info("Contact lookup answers through ContactOut");
        return contactOut;
    }
}
