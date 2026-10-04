package app.lightmove.api.enrichment.common.config;

import app.lightmove.api.core.config.ContactOutSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleClient;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleIndex;
import app.lightmove.api.enrichment.common.service.UnconfiguredContactOutPeopleIndex;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One ContactOut people index, shared by Find executives and Strategy's people search so both draw on
 * the same 60-a-minute pacer; the unconfigured stand-in where the deployment has no ContactOut key.
 */
@Configuration
public class ContactOutPeopleIndexConfig {

    @Bean
    ContactOutPeopleIndex contactOutPeopleIndex(LightMoveProperties properties, VendorClientFactory clientFactory,
                                                  VendorRateLimiter rateLimiter, VendorCallGuard guard) {
        ContactOutSettings contactOut = properties.enrichment().contactout();
        if (contactOut == null || !contactOut.isConfigured()) {
            return new UnconfiguredContactOutPeopleIndex();
        }
        return new ContactOutPeopleClient(contactOut, clientFactory, rateLimiter, guard);
    }
}
