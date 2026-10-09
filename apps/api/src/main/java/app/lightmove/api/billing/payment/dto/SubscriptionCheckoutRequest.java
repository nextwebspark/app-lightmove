package app.lightmove.api.billing.payment.dto;

import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.PlanCode;
import jakarta.validation.constraints.NotNull;

public record SubscriptionCheckoutRequest(
        @NotNull PlanCode planCode,
        @NotNull BillingInterval interval
) {}
