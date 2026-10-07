package app.lightmove.api.billing.plan.dto;

import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.PlanCode;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import java.time.Instant;

/** A workspace's plan and seats, with the contact credits they bring each month. */
public record SubscriptionResponse(PlanCode plan, BillingInterval billingInterval, int seats,
                                   long monthlyContactCredits, SubscriptionStatus status,
                                   Instant currentPeriodStart, Instant currentPeriodEnd) {
}
