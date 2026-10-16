package app.uncava.api.billing.payment.config;

import app.uncava.api.billing.payment.service.PaymentGateway;
import app.uncava.api.billing.payment.service.StripePaymentGateway;
import app.uncava.api.billing.payment.service.UnconfiguredPaymentGateway;
import app.uncava.api.core.config.UncavaProperties;
import app.uncava.api.core.config.StripeSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Stripe where a secret key is configured; without one, every workspace is invoiced. */
@Configuration
@Slf4j
public class PaymentGatewayConfig {

    @Bean
    PaymentGateway paymentGateway(UncavaProperties properties) {
        StripeSettings stripe = properties.billing().stripe();
        if (!stripe.isConfigured()) {
            log.info("Stripe is off — no secret key configured, so every workspace is invoiced.");
            return new UnconfiguredPaymentGateway();
        }
        if (stripe.webhookSecret() == null || stripe.webhookSecret().isBlank()) {
            log.warn("Stripe has no webhook secret: every delivery is refused, so nothing paid is ever granted.");
        }
        return new StripePaymentGateway(stripe);
    }
}
