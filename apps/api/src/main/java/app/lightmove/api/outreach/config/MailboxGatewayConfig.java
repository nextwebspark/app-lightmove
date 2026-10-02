package app.lightmove.api.outreach.config;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.NylasSettings;
import app.lightmove.api.core.config.OutreachSettings;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.service.MailboxGateway;
import app.lightmove.api.outreach.service.NylasMailboxGateway;
import app.lightmove.api.outreach.service.UnconfiguredMailboxGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Picks the {@link MailboxGateway}. A deployment with no mail service configured still boots, offering no outreach. */
@Configuration
@Slf4j
public class MailboxGatewayConfig {

    @Bean
    MailboxGateway mailboxGateway(LightMoveProperties properties, VendorClientFactory clientFactory,
                                  VendorRateLimiter rateLimiter, VendorCallGuard guard) {
        OutreachSettings outreach = properties.outreach();
        NylasSettings nylas = outreach == null ? null : outreach.nylas();
        if (nylas == null || !nylas.isConfigured()) {
            log.info("Outreach email is off — no Nylas application configured, so no mailbox can be connected.");
            return new UnconfiguredMailboxGateway();
        }
        log.info("Outreach email sends through Nylas");
        return new NylasMailboxGateway(nylas, clientFactory, rateLimiter, guard, RestClient.builder());
    }
}
