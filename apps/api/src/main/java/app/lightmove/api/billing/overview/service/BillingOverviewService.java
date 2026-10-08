package app.lightmove.api.billing.overview.service;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.MonthlyCredits;
import app.lightmove.api.billing.credit.model.SourceCredits;
import app.lightmove.api.billing.credit.repository.CreditGrantRepository;
import app.lightmove.api.billing.credit.service.CreditSpendReport;
import app.lightmove.api.billing.overview.constant.PaymentMethodKind;
import app.lightmove.api.billing.overview.dto.BillingPlanSummary;
import app.lightmove.api.billing.overview.dto.BillingResponse;
import app.lightmove.api.billing.overview.dto.BillingUsageResponse;
import app.lightmove.api.billing.overview.dto.ContactCreditsResponse;
import app.lightmove.api.billing.overview.dto.CreditPricesResponse;
import app.lightmove.api.billing.overview.dto.PaymentMethodResponse;
import app.lightmove.api.billing.payment.service.PaymentGateway;
import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import app.lightmove.api.billing.plan.model.BillingMonth;
import app.lightmove.api.billing.plan.model.BillingPlan;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.BillingPlanRepository;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.core.config.CreditPriceSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The billing reads any staff member may make. */
@Service
@RequiredArgsConstructor
public class BillingOverviewService {

    private final WorkspaceSubscriptionRepository subscriptions;
    private final BillingPlanRepository plans;
    private final CreditGrantRepository grants;
    private final CreditSpendReport spend;
    private final PaymentGateway gateway;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BillingResponse overview(UUID workspaceId) {
        Instant now = clock.instant();
        WorkspaceSubscription subscription = subscriptions.findByWorkspaceId(workspaceId).orElse(null);
        BillingPlan plan = subscription == null ? null : plans.findById(subscription.getPlanCode()).orElseThrow();
        CreditPriceSettings prices = properties.billing().prices();
        return new BillingResponse(
                plan == null ? null : new BillingPlanSummary(plan.getCode(), plan.getName()),
                subscription == null ? null : subscription.getBillingInterval(),
                subscription == null ? 0 : subscription.getSeats(),
                plan == null ? null : seatPriceOf(plan, subscription.getBillingInterval()),
                subscription == null ? null : subscription.getStatus(),
                subscription == null ? null : subscription.getCurrentPeriodEnd(),
                creditsOf(workspaceId, subscription, now),
                new CreditPricesResponse(prices.emailFound(), prices.phoneFound()),
                paymentMethodOf(subscription),
                gateway.isOffered());
    }

    @Transactional(readOnly = true)
    public BillingUsageResponse usage(UUID workspaceId) {
        BillingMonth month = BillingMonth.of(subscriptions.findByWorkspaceId(workspaceId).orElse(null),
                clock.instant());
        return new BillingUsageResponse(month.start(), month.end(),
                spend.byMember(workspaceId, month.start(), month.end()));
    }

    /** Every figure is read off the grants spendable now, so {@code left} is always their sum. */
    private ContactCreditsResponse creditsOf(UUID workspaceId, WorkspaceSubscription subscription, Instant now) {
        MonthlyCredits monthly = grants.monthlyCreditsOf(workspaceId, now);
        List<SourceCredits> bySource = grants.spendableBySource(workspaceId, now);
        long left = bySource.stream().mapToLong(SourceCredits::remaining).sum();
        long bought = bySource.stream().filter(credits -> credits.source() == CreditGrantSource.PURCHASED)
                .mapToLong(SourceCredits::remaining).sum();
        long given = bySource.stream().filter(credits -> credits.source().isGivenByHand())
                .mapToLong(SourceCredits::remaining).sum();
        return new ContactCreditsResponse(monthly.granted(), left, bought, given, monthly.usedPercent(),
                monthly.levelAt(left), BillingMonth.of(subscription, now).end());
    }

    private static Long seatPriceOf(BillingPlan plan, BillingInterval interval) {
        return interval == BillingInterval.ANNUAL ? plan.getSeatPriceAnnualFils() : plan.getSeatPriceMonthlyFils();
    }

    private static PaymentMethodResponse paymentMethodOf(WorkspaceSubscription subscription) {
        if (subscription == null || subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            return new PaymentMethodResponse(PaymentMethodKind.NONE, null, null);
        }
        PaymentMethodKind kind = subscription.isBilledByStripe() ? PaymentMethodKind.CARD : PaymentMethodKind.INVOICED;
        return new PaymentMethodResponse(kind, null, null);
    }
}
