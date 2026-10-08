package app.lightmove.api.billing.payment.model;

import java.util.UUID;

/** A Checkout for one pack of contact credits; the pack and its credits ride the session to its webhook. */
public record CreditsCheckout(UUID workspaceId, String customerId, String packCode, long credits, String priceId,
                              String successUrl, String cancelUrl) {
}
