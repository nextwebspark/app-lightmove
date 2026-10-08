package app.lightmove.api.billing.notice.constant;

/** A billing email claimed once per subject: a failed invoice, or a grant of bought credits about to lapse. */
public enum BillingNoticeKind {
    PAYMENT_FAILED,
    PURCHASED_CREDITS_EXPIRING
}
