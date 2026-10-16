package app.uncava.api.enrichment.common.config;

import app.uncava.api.core.config.ContactOutSettings;
import app.uncava.api.core.config.UncavaProperties;
import app.uncava.api.core.resilience.service.VendorCallGuard;
import app.uncava.api.core.resilience.service.VendorClientFactory;
import app.uncava.api.core.resilience.service.VendorRateLimiter;
import app.uncava.api.enrichment.common.service.ContactOutPeopleClient;
import app.uncava.api.enrichment.common.service.ContactOutPeopleIndex;
import app.uncava.api.enrichment.common.service.UnconfiguredContactOutPeopleIndex;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One ContactOut people index, shared by Find executives and Strategy's people search so both draw on
 * the same 60-a-minute pacer; the unconfigured stand-in where the deployment has no ContactOut key.
 */
@Configuration
public class ContactOutPeopleIndexConfig {

    @Bean
    ContactOutPeopleIndex contactOutPeopleIndex(UncavaProperties properties, VendorClientFactory clientFactory,
                                                  VendorRateLimiter rateLimiter, VendorCallGuard guard) {
        ContactOutSettings contactOut = properties.enrichment().contactout();
        if (contactOut == null || !contactOut.isConfigured()) {
            return new UnconfiguredContactOutPeopleIndex();
        }
        return new ContactOutPeopleClient(contactOut, clientFactory, rateLimiter, guard);
    }
}
