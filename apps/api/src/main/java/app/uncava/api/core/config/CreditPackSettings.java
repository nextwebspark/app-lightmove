package app.uncava.api.core.config;

/** One pack of contact credits a workspace can buy — {@code uncava.billing.packs.<code>.*}; price before VAT. */
public record CreditPackSettings(
        long credits,
        long priceFils,
        String stripePriceId
) {}
