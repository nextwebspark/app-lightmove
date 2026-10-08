package app.lightmove.api.core.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Billing and the contact-credit ledger — {@code lightmove.billing.*}. A spend the credits cannot cover is refused
 * while {@code enforce} is on, and recorded as an overdraft with it off.
 */
public record BillingSettings(
        @DefaultValue("true") boolean enforce,
        /** How long a hold may wait for its capture before the sweeper releases it. */
        @DefaultValue("15m") Duration holdTtl,
        @DefaultValue("5m") Duration sweepInterval,
        @DefaultValue BillingJobSettings jobs,
        @DefaultValue CreditPriceSettings prices,
        @DefaultValue FairUseSettings fairUse,
        @DefaultValue GrandfatherSettings grandfather,
        /** How long the month's credits keep being granted after a payment fails. */
        @DefaultValue("7d") Duration pastDueGrace,
        Map<String, CreditPackSettings> packs,
        @DefaultValue StripeSettings stripe
) {

    public Map<String, CreditPackSettings> packs() {
        return packs == null ? Map.of() : packs;
    }
}
