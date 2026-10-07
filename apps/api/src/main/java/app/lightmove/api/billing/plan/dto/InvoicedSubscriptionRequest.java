package app.lightmove.api.billing.plan.dto;

import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.PlanCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

/** What an invoiced workspace is on. {@code contactCreditPool} is read on Enterprise only, where it is required. */
public record InvoicedSubscriptionRequest(
        @NotNull PlanCode plan,
        @NotNull BillingInterval billingInterval,
        @PositiveOrZero int seats,
        @PositiveOrZero Integer contactCreditPool,
        Instant currentPeriodStart,
        Instant currentPeriodEnd
) {}
