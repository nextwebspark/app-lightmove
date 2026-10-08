package app.lightmove.api.billing.plan.model;

import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.PlanCode;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import java.time.Instant;

/** A subscription as the payment provider reports it, read into billing's own terms. */
public record PaidSubscription(String customerId, String subscriptionId, PlanCode plan, BillingInterval interval,
                               int seats, SubscriptionStatus status, Instant periodStart, Instant periodEnd) {
}
