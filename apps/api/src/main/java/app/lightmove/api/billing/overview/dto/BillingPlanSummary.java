package app.lightmove.api.billing.overview.dto;

import app.lightmove.api.billing.plan.constant.PlanCode;

public record BillingPlanSummary(PlanCode code, String name) {
}
