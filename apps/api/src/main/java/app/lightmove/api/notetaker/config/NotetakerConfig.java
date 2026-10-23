package app.lightmove.api.notetaker.config;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.RecallSettings;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.notetaker.service.RecallBotApi;
import app.lightmove.api.notetaker.service.RecallBotClient;
import app.lightmove.api.notetaker.service.UnconfiguredRecallBotApi;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Recall.ai's bots where a key and a bot webhook secret are configured; without both, no Notetaker is offered. */
@Configuration
@Slf4j
public class NotetakerConfig {

    @Bean
    RecallBotApi recallBotApi(LightMoveProperties properties, VendorClientFactory clientFactory,
                              VendorRateLimiter rateLimiter, VendorCallGuard guard, Clock clock) {
        RecallSettings recall = properties.recall();
        if (recall == null || !recall.isBotOffered()) {
            log.info("Notetaker is off — no Recall API key or bot webhook secret configured.");
            return new UnconfiguredRecallBotApi();
        }
        log.info("Notetaker bots are sent through Recall at {}", recall.baseUrl());
        return new RecallBotClient(recall, clientFactory, rateLimiter, guard, RestClient.builder(), clock);
    }
}
