package app.lightmove.api.billing.payment.service;

import app.lightmove.api.billing.payment.model.PlanPrice;
import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.PlanCode;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.StripePriceSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The Stripe seat price of each self-serve plan and interval, and back again from a price a webhook names. */
@Component
@RequiredArgsConstructor
public class PlanPrices {

    private final LightMoveProperties properties;

    public Optional<String> priceOf(PlanCode plan, BillingInterval interval) {
        return Optional.ofNullable(byPlan().get(new PlanPrice(plan, interval))).filter(id -> !id.isBlank());
    }

    public Optional<PlanPrice> planOf(String priceId) {
        return byPlan().entrySet().stream()
                .filter(price -> priceId != null && priceId.equals(price.getValue()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private Map<PlanPrice, String> byPlan() {
        StripePriceSettings prices = properties.billing().stripe().prices();
        Map<PlanPrice, String> byPlan = new HashMap<>();
        byPlan.put(new PlanPrice(PlanCode.CORE, BillingInterval.MONTHLY), prices.coreMonthly());
        byPlan.put(new PlanPrice(PlanCode.CORE, BillingInterval.ANNUAL), prices.coreAnnual());
        byPlan.put(new PlanPrice(PlanCode.PRO, BillingInterval.MONTHLY), prices.proMonthly());
        byPlan.put(new PlanPrice(PlanCode.PRO, BillingInterval.ANNUAL), prices.proAnnual());
        return byPlan;
    }
}
