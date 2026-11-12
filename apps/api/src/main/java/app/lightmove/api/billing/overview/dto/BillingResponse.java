package app.lightmove.api.billing.overview.dto;

import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import java.time.Instant;
import java.util.List;

/**
 * Settings → Billing and the credit chip in one read. Plan fields null without a subscription; {@code seatPriceFils}
 * null on a per-workspace plan; {@code plans} and {@code packs} empty without Stripe or {@code BILLING_MANAGE};
 * {@code trialEndsAt} only on an unpaid app trial (its {@code seats} are the staff); {@code enforced} false shows none.
 */
public record BillingResponse(BillingPlanSummary plan, BillingInterval interval, int seats, Long seatPriceFils,
                              SubscriptionStatus status, Instant renewsAt, ContactCreditsResponse credits,
                              CreditPricesResponse prices, PaymentMethodResponse paymentMethod,
                              boolean stripeOffered, List<BillingPlanOffer> plans, List<CreditPackOffer> packs,
                              Instant trialEndsAt, String contactEmail, boolean enforced) {
}
