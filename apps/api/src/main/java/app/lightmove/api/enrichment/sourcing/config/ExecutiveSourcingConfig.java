package app.lightmove.api.enrichment.sourcing.config;

import app.lightmove.api.core.config.BrightDataSettings;
import app.lightmove.api.core.config.ExecutiveSourcingSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleIndex;
import app.lightmove.api.enrichment.sourcing.service.BusinessSearchProfileIndex;
import app.lightmove.api.enrichment.sourcing.service.ContactOutLiveProfiles;
import app.lightmove.api.enrichment.sourcing.service.LiveProfiles;
import app.lightmove.api.enrichment.sourcing.service.ProfileIndex;
import app.lightmove.api.enrichment.sourcing.service.UnconfiguredLiveProfiles;
import app.lightmove.api.enrichment.sourcing.service.UnconfiguredProfileIndex;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Find executives' two sources, each its stand-in where unconfigured: ContactOut's People Search as the live index
 * wherever a ContactOut key is set, Bright Data's Business Search as the profile index wherever a Bright Data key is.
 */
@Configuration
@Slf4j
public class ExecutiveSourcingConfig {

    @Bean
    LiveProfiles liveProfiles(ContactOutPeopleIndex index, ObjectMapper json, LightMoveProperties properties) {
        if (!index.isOffered()) {
            log.info("Find executives has no live index — no ContactOut key");
            return new UnconfiguredLiveProfiles();
        }
        ExecutiveSourcingSettings settings = properties.enrichment().sourcing();
        return new ContactOutLiveProfiles(index.waitingUpTo(settings.livePermitWait()), json);
    }

    @Bean
    ProfileIndex profileIndex(LightMoveProperties properties, VendorClientFactory clientFactory,
                              VendorRateLimiter rateLimiter, VendorCallGuard guard, ObjectMapper json, Clock clock) {
        BrightDataSettings brightData = properties.enrichment().brightdata();
        ExecutiveSourcingSettings settings = properties.enrichment().sourcing();
        if (!settings.businessSearch().enabled() || brightData.apiKey() == null || brightData.apiKey().isBlank()) {
            log.info("Find executives has no profile index — Business Search is off or has no Bright Data key");
            return new UnconfiguredProfileIndex();
        }
        return new BusinessSearchProfileIndex(brightData, settings.businessSearch(), clientFactory, rateLimiter, guard,
                json, clock);
    }
}
