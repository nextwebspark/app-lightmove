package app.lightmove.api.billing.payment.service;

import app.lightmove.api.billing.payment.model.CreditsCheckout;
import app.lightmove.api.billing.payment.model.PaymentEvent;
import app.lightmove.api.billing.payment.model.SeatQuantityChange;
import app.lightmove.api.billing.payment.model.SubscriptionCheckout;
import java.util.UUID;

/** Where money is taken: Stripe where a key is configured, nowhere otherwise. Each call returns a page to send the admin to. */
public interface PaymentGateway {

    boolean isOffered();

    /** @return the new customer's id */
    String createCustomer(UUID workspaceId, String name);

    /** Whether the customer already pays for a subscription Stripe has not yet told us about. */
    boolean hasLiveSubscription(String customerId);

    /** Expires the customer's subscription Checkouts still open, so two never both get paid. */
    String subscriptionCheckout(SubscriptionCheckout checkout);

    String creditsCheckout(CreditsCheckout checkout);

    String portal(String customerId, String returnUrl);

    /** Sets the subscription's seat quantity: an added seat is invoiced now, a removed one simply bills no more. */
    SeatQuantityChange updateSeats(String subscriptionId, long seats);

    /** Verifies a webhook delivery's signature before reading a byte of it; refused with {@code BILLING_WEBHOOK_REJECTED}. */
    PaymentEvent eventOf(byte[] payload, String signature);
}
