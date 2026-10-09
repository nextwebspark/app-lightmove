package app.lightmove.api.billing.plan.dto;

import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.PlanCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

/** {@code contactCreditPool} is required on Enterprise and refused on any other plan. */
public record InvoicedSubscriptionRequest(
        @NotNull PlanCode plan,
        @NotNull BillingInterval billingInterval,
        @PositiveOrZero int seats,
        @PositiveOrZero Integer contactCreditPool,
        Instant currentPeriodStart,
        Instant currentPeriodEnd
) {}
