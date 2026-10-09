package app.lightmove.api.billing.overview.dto;

import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.PlanCode;
import java.util.List;

/**
 * One plan of the catalogue as the plans dialog draws it: prices per staff seat a month, before VAT, and null with
 * the credits on a plan agreed per workspace. {@code checkoutIntervals} are the ones Stripe sells it in here.
 */
public record BillingPlanOffer(PlanCode code, String name, Long seatPriceMonthlyFils, Long seatPriceAnnualFils,
                               Integer contactCreditsPerSeat, boolean custom, List<BillingInterval> checkoutIntervals) {
}
