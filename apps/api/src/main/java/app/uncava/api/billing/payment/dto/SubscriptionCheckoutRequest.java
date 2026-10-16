package app.uncava.api.billing.payment.dto;

import app.uncava.api.billing.plan.constant.BillingInterval;
import app.uncava.api.billing.plan.constant.PlanCode;
import jakarta.validation.constraints.NotNull;

public record SubscriptionCheckoutRequest(
        @NotNull PlanCode planCode,
        @NotNull BillingInterval interval
) {}
