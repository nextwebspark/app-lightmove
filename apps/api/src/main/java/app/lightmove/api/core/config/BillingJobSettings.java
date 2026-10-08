package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Billing's scheduled jobs, as UTC crons — {@code lightmove.billing.jobs.*}. */
public record BillingJobSettings(
        @DefaultValue("0 7 * * * *") String monthlyReset,
        @DefaultValue("0 40 2 * * *") String reconcile,
        /** Retries the Stripe seat quantities a membership change could not sync. */
        @DefaultValue("0 */5 * * * *") String seatSync
) {}
