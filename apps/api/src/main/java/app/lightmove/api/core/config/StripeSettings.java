package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Stripe — {@code lightmove.billing.stripe.*}. A blank secret key is a deployment that takes no payment: every
 * workspace is invoiced, the checkout and portal endpoints answer {@code BILLING_UNAVAILABLE}, and a blank webhook
 * secret refuses every delivery.
 */
public record StripeSettings(
        String secretKey,
        String webhookSecret,
        @DefaultValue StripePriceSettings prices
) {

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    @Override
    public String toString() {
        return "StripeSettings[secretKey=<redacted>, webhookSecret=<redacted>, prices=" + prices + "]";
    }
}
