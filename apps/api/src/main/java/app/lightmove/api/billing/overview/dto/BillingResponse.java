package app.lightmove.api.billing.overview.dto;

import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import java.time.Instant;
import java.util.List;

/**
 * Settings → Billing and the credit chip in one read. The plan's fields are null for a workspace with no
 * subscription, and {@code seatPriceFils} on a plan priced per workspace. {@code plans} and {@code packs} are what an
 * admin may buy, both empty where Stripe is not offered and for anyone without {@code BILLING_MANAGE}.
 * {@code trialEndsAt} is set only on the app's own trial, still unpaid, and its {@code seats} are the staff.
 */
public record BillingResponse(BillingPlanSummary plan, BillingInterval interval, int seats, Long seatPriceFils,
                              SubscriptionStatus status, Instant renewsAt, ContactCreditsResponse credits,
                              CreditPricesResponse prices, PaymentMethodResponse paymentMethod,
                              boolean stripeOffered, List<BillingPlanOffer> plans, List<CreditPackOffer> packs,
                              Instant trialEndsAt) {
}
