package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Billing and the contact-credit ledger — {@code lightmove.billing.*}. While {@code enforce} is off a spend the
 * credits cannot cover is recorded as an overdraft rather than refused.
 */
public record BillingSettings(
        @DefaultValue("false") boolean enforce,
        /** How long a hold may wait for its capture before the sweeper releases it. */
        @DefaultValue("15m") Duration holdTtl,
        @DefaultValue("5m") Duration sweepInterval,
        @DefaultValue CreditPriceSettings prices,
        @DefaultValue FairUseSettings fairUse
) {}
