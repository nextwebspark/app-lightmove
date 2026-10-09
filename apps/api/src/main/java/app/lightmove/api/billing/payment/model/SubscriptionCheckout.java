package app.lightmove.api.billing.payment.model;

import java.util.UUID;

/** A Checkout for a per-seat subscription, quantity the workspace's staff seats. */
public record SubscriptionCheckout(UUID workspaceId, String customerId, String priceId, long seats,
                                   String successUrl, String cancelUrl) {
}
