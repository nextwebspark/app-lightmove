package app.lightmove.api.billing.credit.constant;

/** A hold's life: reserved, then spent or given back; a spent one may later be refunded. */
public enum CreditHoldStatus {
    OPEN,
    CAPTURED,
    RELEASED,
    REFUNDED
}
