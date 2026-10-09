package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Billing's scheduled jobs, as UTC crons — {@code lightmove.billing.jobs.*}. */
public record BillingJobSettings(
        @DefaultValue("0 7 * * * *") String monthlyReset,
        @DefaultValue("0 40 2 * * *") String reconcile,
        /** Retries the Stripe seat quantities a membership change could not sync. */
        @DefaultValue("0 */5 * * * *") String seatSync,
        /** Warns of bought credits lapsing within a week. */
        @DefaultValue("0 20 6 * * *") String purchasedCreditExpiry,
        /** Warns of a trial ending within three days, and says when it has. */
        @DefaultValue("0 30 6 * * *") String trialNotices
) {}
