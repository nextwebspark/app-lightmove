package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Outreach email from a consultant's own mailbox — {@code lightmove.outreach.*}. */
public record OutreachSettings(
        NylasSettings nylas,

        /** How long a started mailbox connection may take to come back from the provider's consent screen. */
        @DefaultValue("10m") Duration connectWindow,

        /** Emails one mailbox sends a day, whatever its sequences ask for: a mailbox that bursts is a mailbox flagged. */
        @DefaultValue("50") int dailyCap
) {}
