package app.lightmove.api.billing.payment.model;

import java.util.UUID;

/** A Checkout for one pack of contact credits; the pack's code rides the session to its webhook. */
public record CreditsCheckout(UUID workspaceId, String customerId, String packCode, String priceId,
                              String successUrl, String cancelUrl) {
}
