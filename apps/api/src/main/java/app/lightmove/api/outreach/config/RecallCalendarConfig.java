package app.lightmove.api.outreach.config;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.RecallSettings;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.service.RecallCalendarApi;
import app.lightmove.api.outreach.service.RecallCalendarClient;
import app.lightmove.api.outreach.service.UnconfiguredRecallCalendarApi;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Recall.ai's Calendar API where a key is configured; without one, no calendar is ever handed to it. */
@Configuration
@Slf4j
public class RecallCalendarConfig {

    @Bean
    RecallCalendarApi recallCalendarApi(LightMoveProperties properties, VendorClientFactory clientFactory,
                                        VendorRateLimiter rateLimiter, VendorCallGuard guard, Clock clock) {
        RecallSettings recall = properties.recall();
        if (recall == null || !recall.isConfigured()) {
            log.info("Recall is off — no Recall API key configured, so calendars are read directly.");
            return new UnconfiguredRecallCalendarApi();
        }
        log.info("Calendars of direct mailboxes are read through Recall at {}", recall.baseUrl());
        return new RecallCalendarClient(recall, clientFactory, rateLimiter, guard, RestClient.builder(), clock);
    }
}
