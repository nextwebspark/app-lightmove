package app.uncava.api.billing.payment.model;

import app.uncava.api.billing.plan.constant.BillingInterval;
import app.uncava.api.billing.plan.constant.PlanCode;

/** The plan and interval a Stripe seat price stands for. */
public record PlanPrice(PlanCode plan, BillingInterval interval) {
}
