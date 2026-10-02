package app.lightmove.api.outreach.config;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.NylasSettings;
import app.lightmove.api.core.config.OutreachSettings;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.service.DirectMailboxGateway;
import app.lightmove.api.outreach.service.MailboxGateway;
import app.lightmove.api.outreach.service.NylasMailboxGateway;
import app.lightmove.api.outreach.service.RoutingMailboxGateway;
import app.lightmove.api.outreach.service.UnconfiguredMailboxGateway;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * Wires the {@link MailboxGateway} everything injects: a {@link RoutingMailboxGateway} over Nylas and whichever of
 * our own gateways exist. A deployment with no mail service configured still boots, offering no outreach.
 */
@Configuration
@Slf4j
public class MailboxGatewayConfig {

    /** The qualifier of the Nylas gateway the router falls back to; a test's recording gateway takes its place. */
    public static final String NYLAS = "nylasMailboxGateway";

    /** {@code @Fallback}, so a test's gateway under the same qualifier wins without displacing the router. */
    @Bean
    @Qualifier(NYLAS)
    @Fallback
    MailboxGateway nylasMailboxGateway(LightMoveProperties properties, VendorClientFactory clientFactory,
                                       VendorRateLimiter rateLimiter, VendorCallGuard guard) {
        OutreachSettings outreach = properties.outreach();
        NylasSettings nylas = outreach == null ? null : outreach.nylas();
        if (nylas == null || !nylas.isConfigured()) {
            log.info("Nylas is off — no Nylas application configured.");
            return new UnconfiguredMailboxGateway();
        }
        log.info("Nylas is configured for outreach email");
        return new NylasMailboxGateway(nylas, clientFactory, rateLimiter, guard, RestClient.builder());
    }

    @Bean
    @Primary
    RoutingMailboxGateway mailboxGateway(@Qualifier(NYLAS) MailboxGateway nylas,
                                         ObjectProvider<DirectMailboxGateway> direct, LightMoveProperties properties) {
        MailboxGatewayKind connectThrough = connectThrough(properties.outreach());
        log.info("New mailboxes connect through {}", connectThrough);
        return new RoutingMailboxGateway(nylas, direct.orderedStream().toList(), connectThrough);
    }

    private static MailboxGatewayKind connectThrough(OutreachSettings outreach) {
        String chosen = outreach == null || outreach.gateway() == null ? "" : outreach.gateway().strip();
        return chosen.isEmpty() ? MailboxGatewayKind.NYLAS
                : MailboxGatewayKind.valueOf(chosen.toUpperCase(Locale.ROOT));
    }
}
