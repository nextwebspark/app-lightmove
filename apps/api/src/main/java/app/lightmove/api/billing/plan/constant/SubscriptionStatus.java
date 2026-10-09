package app.lightmove.api.billing.plan.constant;

/** Where a workspace's subscription stands. {@code INVOICED} is billed outside Stripe and granted credits by hand. */
public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELLED,
    INVOICED
}
