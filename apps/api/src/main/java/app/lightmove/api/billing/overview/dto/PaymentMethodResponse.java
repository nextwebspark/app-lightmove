package app.lightmove.api.billing.overview.dto;

import app.lightmove.api.billing.overview.constant.PaymentMethodKind;

/** {@code brand} and {@code last4} only on a card, and only once the payment gateway reads them. */
public record PaymentMethodResponse(PaymentMethodKind kind, String brand, String last4) {
}
