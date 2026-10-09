package app.lightmove.api.billing.payment.model;

import java.util.UUID;

/** A workspace's subscription invoice could not be paid. Published inside the webhook's transaction. */
public record SubscriptionPaymentFailed(UUID workspaceId, String invoiceId) {
}
