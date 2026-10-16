package app.uncava.api.billing.overview.dto;

import app.uncava.api.billing.plan.constant.PlanCode;

public record BillingPlanSummary(PlanCode code, String name) {
}
