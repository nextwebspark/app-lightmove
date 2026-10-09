package app.lightmove.api.billing.notice.constant;

/**
 * A billing email claimed once per subject: a failed invoice, a grant of bought credits about to lapse, or a
 * workspace's trial ending and then ended.
 */
public enum BillingNoticeKind {
    PAYMENT_FAILED,
    PURCHASED_CREDITS_EXPIRING,
    TRIAL_ENDING,
    TRIAL_ENDED
}
