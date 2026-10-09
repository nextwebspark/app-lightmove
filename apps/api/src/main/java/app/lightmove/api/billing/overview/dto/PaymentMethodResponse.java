package app.lightmove.api.billing.overview.dto;

import app.lightmove.api.billing.overview.constant.PaymentMethodKind;

/** How the workspace pays; which card is {@code GET /billing/card}'s, asked of Stripe only where the page shows it. */
public record PaymentMethodResponse(PaymentMethodKind kind) {
}
