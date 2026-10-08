package app.lightmove.api.core.config;

/**
 * The Stripe price of a staff seat on each self-serve plan — {@code lightmove.billing.stripe.prices.*}. Ids differ
 * between Stripe's test and live mode, so they are a deployment's, never the plan catalogue's.
 */
public record StripePriceSettings(
        String coreMonthly,
        String coreAnnual,
        String proMonthly,
        String proAnnual
) {}
