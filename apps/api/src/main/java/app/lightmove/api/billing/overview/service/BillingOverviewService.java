package app.lightmove.api.billing.overview.service;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.MonthlyCredits;
import app.lightmove.api.billing.credit.model.SourceCredits;
import app.lightmove.api.billing.credit.repository.CreditGrantRepository;
import app.lightmove.api.billing.credit.service.CreditSpendReport;
import app.lightmove.api.billing.overview.constant.PaymentMethodKind;
import app.lightmove.api.billing.overview.dto.BillingPlanOffer;
import app.lightmove.api.billing.overview.dto.BillingPlanSummary;
import app.lightmove.api.billing.overview.dto.BillingResponse;
import app.lightmove.api.billing.overview.dto.BillingUsageResponse;
import app.lightmove.api.billing.overview.dto.ContactCreditsResponse;
import app.lightmove.api.billing.overview.dto.CreditPackOffer;
import app.lightmove.api.billing.overview.dto.CreditPricesResponse;
import app.lightmove.api.billing.overview.dto.PaymentCardResponse;
import app.lightmove.api.billing.overview.dto.PaymentMethodResponse;
import app.lightmove.api.billing.payment.model.PaymentCard;
import app.lightmove.api.billing.payment.service.PaymentGateway;
import app.lightmove.api.billing.payment.service.PlanPrices;
import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import app.lightmove.api.billing.plan.model.BillingMonth;
import app.lightmove.api.billing.plan.model.BillingPlan;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.BillingPlanRepository;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.billing.plan.service.BillingSeats;
import app.lightmove.api.core.config.CreditPriceSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The billing reads any staff member may make. */
@Service
@RequiredArgsConstructor
public class BillingOverviewService {

    private static final Duration CARD_TTL = Duration.ofMinutes(1);

    private final WorkspaceSubscriptionRepository subscriptions;
    private final BillingPlanRepository plans;
    private final CreditGrantRepository grants;
    private final CreditSpendReport spend;
    private final BillingSeats seats;
    private final PaymentGateway gateway;
    private final PlanPrices planPrices;
    private final LightMoveProperties properties;
    private final WorkspaceAccess access;
    private final Clock clock;
    private final Cache<String, Optional<PaymentCard>> cards =
            Caffeine.newBuilder().expireAfterWrite(CARD_TTL).maximumSize(10_000).build();

    /** The catalogue goes only to whoever may buy from it. */
    @Transactional(readOnly = true)
    public BillingResponse overview(UUID workspaceId, UUID userId) {
        Instant now = clock.instant();
        WorkspaceSubscription subscription = subscriptions.findByWorkspaceId(workspaceId).orElse(null);
        BillingPlan plan = subscription == null ? null : plans.findById(subscription.getPlanCode()).orElseThrow();
        CreditPriceSettings prices = properties.billing().prices();
        boolean offered = gateway.isOffered();
        boolean buys = offered && access.holdsAction(userId, workspaceId, WorkspaceAction.BILLING_MANAGE);
        boolean trial = subscription != null && subscription.isAppTrial();
        return new BillingResponse(
                plan == null ? null : new BillingPlanSummary(plan.getCode(), plan.getName()),
                subscription == null ? null : subscription.getBillingInterval(),
                seatsOf(workspaceId, subscription, now),
                plan == null ? null : seatPriceOf(plan, subscription.getBillingInterval()),
                subscription == null ? null : subscription.getStatus(),
                subscription == null ? null : subscription.getCurrentPeriodEnd(),
                creditsOf(workspaceId, subscription, now),
                new CreditPricesResponse(prices.emailFound(), prices.phoneFound()),
                paymentMethodOf(subscription),
                offered,
                buys ? planOffers() : List.of(),
                buys ? packOffers() : List.of(),
                trial ? subscription.getTrialEndsAt() : null,
                properties.billing().contactEmail());
    }

    /** Asked of Stripe outside any transaction and never stored; an answer, a miss included, is held a minute. */
    public PaymentCardResponse card(UUID workspaceId) {
        return subscriptions.findByWorkspaceId(workspaceId)
                .filter(WorkspaceSubscription::isBilledByStripe)
                .flatMap(subscription -> cards.get(subscription.getStripeSubscriptionId(), gateway::cardOf))
                .map(card -> new PaymentCardResponse(card.brand(), card.last4()))
                .orElseGet(() -> new PaymentCardResponse(null, null));
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
                monthly.levelAt(left), resetsAtOf(subscription, now));
    }

    /** A trial has no seats of its own: it shows the staff fair use counts, which never passes an int. */
    private int seatsOf(UUID workspaceId, WorkspaceSubscription subscription, Instant now) {
        if (subscription == null) {
            return 0;
        }
        return subscription.isAppTrial()
                ? Math.toIntExact(seats.allowanceOf(workspaceId, now).staffSeats()) : subscription.getSeats();
    }

    /** A trial's credits do not reset: they lapse with it. */
    private static Instant resetsAtOf(WorkspaceSubscription subscription, Instant now) {
        return subscription != null && subscription.isAppTrial()
                ? subscription.getTrialEndsAt() : BillingMonth.of(subscription, now).end();
    }

    private List<BillingPlanOffer> planOffers() {
        return plans.findAllByOrderBySortOrder().stream()
                .map(plan -> new BillingPlanOffer(plan.getCode(), plan.getName(), plan.getSeatPriceMonthlyFils(),
                        plan.getSeatPriceAnnualFils(), plan.getContactCreditsPerSeat(), plan.isCustom(),
                        Arrays.stream(BillingInterval.values())
                                .filter(interval -> planPrices.priceOf(plan.getCode(), interval).isPresent())
                                .toList()))
                .toList();
    }

    private List<CreditPackOffer> packOffers() {
        return properties.billing().packs().entrySet().stream()
                .filter(pack -> pack.getValue().stripePriceId() != null && !pack.getValue().stripePriceId().isBlank())
                .map(pack -> new CreditPackOffer(pack.getKey(), pack.getValue().credits(), pack.getValue().priceFils()))
                .sorted(Comparator.comparingLong(CreditPackOffer::credits))
                .toList();
    }

    private static Long seatPriceOf(BillingPlan plan, BillingInterval interval) {
        return interval == BillingInterval.ANNUAL ? plan.getSeatPriceAnnualFils() : plan.getSeatPriceMonthlyFils();
    }

    private static PaymentMethodResponse paymentMethodOf(WorkspaceSubscription subscription) {
        if (subscription == null || subscription.getStatus() == SubscriptionStatus.CANCELLED
                || subscription.isAppTrial()) {
            return new PaymentMethodResponse(PaymentMethodKind.NONE);
        }
        PaymentMethodKind kind = subscription.isBilledByStripe() ? PaymentMethodKind.CARD : PaymentMethodKind.INVOICED;
        return new PaymentMethodResponse(kind);
    }
}
