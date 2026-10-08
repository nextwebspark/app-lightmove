package app.lightmove.api.billing.payment.model;

import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.PlanCode;

/** The plan and interval a Stripe seat price stands for. */
public record PlanPrice(PlanCode plan, BillingInterval interval) {
}
