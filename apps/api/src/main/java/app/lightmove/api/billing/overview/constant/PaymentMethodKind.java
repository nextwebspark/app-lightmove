package app.lightmove.api.billing.overview.constant;

/** How a workspace pays: by invoice outside Stripe, by a card Stripe holds, or not at all yet. */
public enum PaymentMethodKind {
    INVOICED,
    CARD,
    NONE
}
