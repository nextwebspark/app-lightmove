package app.lightmove.api.billing.payment.model;

import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import java.time.Instant;

/**
 * A Stripe subscription as one event reports it: its per-seat price, how many seats, the period and where it stands.
 *
 * @param status null while Stripe still waits for the first payment ({@code incomplete}), which changes nothing here
 */
public record StripeSubscriptionState(String customerId, String subscriptionId, SubscriptionStatus status,
                                      String priceId, long seats, Instant periodStart, Instant periodEnd) {
}
