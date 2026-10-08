package app.lightmove.api.billing.overview.service;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.MonthlyCredits;
import app.lightmove.api.billing.credit.repository.CreditGrantRepository;
import app.lightmove.api.billing.credit.service.CreditLedger;
import app.lightmove.api.billing.credit.service.CreditSpendReport;
import app.lightmove.api.billing.overview.constant.PaymentMethodKind;
import app.lightmove.api.billing.overview.dto.BillingPlanSummary;
import app.lightmove.api.billing.overview.dto.BillingResponse;
import app.lightmove.api.billing.overview.dto.BillingUsageResponse;
import app.lightmove.api.billing.overview.dto.ContactCreditsResponse;
import app.lightmove.api.billing.overview.dto.CreditPricesResponse;
import app.lightmove.api.billing.overview.dto.MemberCreditUsageResponse;
import app.lightmove.api.billing.overview.dto.PaymentMethodResponse;
import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.model.BillingMonth;
import app.lightmove.api.billing.plan.model.BillingPlan;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.BillingPlanRepository;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.core.config.CreditPriceSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The billing reads any staff member may make; a pure client is told there is nothing here. */
@Service
@RequiredArgsConstructor
public class BillingOverviewService {

    private static final List<CreditGrantSource> GIVEN =
            List.of(CreditGrantSource.PROMO, CreditGrantSource.MANUAL, CreditGrantSource.GOODWILL);

    private final WorkspaceSubscriptionRepository subscriptions;
    private final BillingPlanRepository plans;
    private final CreditGrantRepository grants;
    private final CreditLedger ledger;
    private final CreditSpendReport spend;
    private final WorkspaceAccess access;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BillingResponse overview(UUID userId, UUID workspaceId) {
        requireStaff(userId, workspaceId);
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
                false);
    }

    @Transactional(readOnly = true)
    public BillingUsageResponse usage(UUID userId, UUID workspaceId) {
        requireStaff(userId, workspaceId);
        BillingMonth month = BillingMonth.of(subscriptions.findByWorkspaceId(workspaceId).orElse(null),
                clock.instant());
        List<MemberCreditUsageResponse> members = spend.byMember(workspaceId, month.start(), month.end()).stream()
                .map(member -> new MemberCreditUsageResponse(member.userId(), member.name(), member.emailsFound(),
                        member.phonesFound(), member.creditsSpent()))
                .toList();
        return new BillingUsageResponse(month.start(), month.end(), members);
    }

    private ContactCreditsResponse creditsOf(UUID workspaceId, WorkspaceSubscription subscription, Instant now) {
        MonthlyCredits monthly = grants.monthlyCreditsOf(workspaceId, now);
        long available = ledger.balanceOf(workspaceId).available();
        return new ContactCreditsResponse(
                monthly.granted(),
                Math.max(available, 0),
                grants.sumSpendable(workspaceId, List.of(CreditGrantSource.PURCHASED), now),
                grants.sumSpendable(workspaceId, GIVEN, now),
                monthly.usedPercent(),
                monthly.levelAt(available),
                BillingMonth.of(subscription, now).end());
    }

    private void requireStaff(UUID userId, UUID workspaceId) {
        if (!access.isStaff(userId, workspaceId)) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
    }

    private static Long seatPriceOf(BillingPlan plan, BillingInterval interval) {
        return interval == BillingInterval.ANNUAL ? plan.getSeatPriceAnnualFils() : plan.getSeatPriceMonthlyFils();
    }

    private static PaymentMethodResponse paymentMethodOf(WorkspaceSubscription subscription) {
        if (subscription == null) {
            return new PaymentMethodResponse(PaymentMethodKind.NONE, null, null);
        }
        PaymentMethodKind kind = subscription.isBilledByStripe() ? PaymentMethodKind.CARD : PaymentMethodKind.INVOICED;
        return new PaymentMethodResponse(kind, null, null);
    }
}
