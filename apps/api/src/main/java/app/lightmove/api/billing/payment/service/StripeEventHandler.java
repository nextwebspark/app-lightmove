package app.lightmove.api.billing.payment.service;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.credit.model.CreditGrantReceipt;
import app.lightmove.api.billing.credit.repository.CreditGrantRepository;
import app.lightmove.api.billing.credit.service.CreditLedger;
import app.lightmove.api.billing.credit.service.MonthlyCreditReset;
import app.lightmove.api.billing.payment.model.PaymentEvent;
import app.lightmove.api.billing.payment.model.PlanPrice;
import app.lightmove.api.billing.payment.model.StripeSubscriptionState;
import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.PlanCode;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import app.lightmove.api.billing.plan.model.BillingMonth;
import app.lightmove.api.billing.plan.model.BillingPlan;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.BillingPlanRepository;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns verified Stripe events into the subscription's state and contact-credit grants. Each event is claimed and
 * handled in one transaction, so a replay changes nothing and a failure is retried whole by Stripe. A paid period's
 * month is granted under {@link MonthlyCreditReset#grantKey}, the job's own key, so the two never both grant it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeEventHandler {

    private static final int PURCHASED_CREDITS_MONTHS = 12;

    private final BillingWebhookEvents events;
    private final BillingCustomers customers;
    private final WorkspaceSubscriptionRepository subscriptions;
    private final BillingPlanRepository plans;
    private final CreditGrantRepository grants;
    private final PlanPrices prices;
    private final CreditLedger ledger;
    private final AuditService audit;
    private final Clock clock;

    @Transactional
    public void handle(PaymentEvent event) {
        if (event instanceof PaymentEvent.Ignored || !events.claim(event.eventId(), event.type())) {
            return;
        }
        switch (event) {
            case PaymentEvent.SubscriptionChanged changed ->
                    workspaceOf(changed.subscription().customerId(), event)
                            .ifPresent(workspaceId -> follow(workspaceId, changed.subscription(), event.createdAt()));
            case PaymentEvent.InvoicePaid paid ->
                    workspaceOf(paid.customerId(), event).ifPresent(workspaceId -> paid(workspaceId, paid));
            case PaymentEvent.InvoicePaymentFailed failed ->
                    workspaceOf(failed.customerId(), event).ifPresent(workspaceId -> failed(workspaceId, failed));
            case PaymentEvent.CreditsPaid bought ->
                    workspaceOf(bought.customerId(), event).ifPresent(workspaceId -> credit(workspaceId, bought));
            case PaymentEvent.Ignored ignored -> {
            }
        }
    }

    private void follow(UUID workspaceId, StripeSubscriptionState state, Instant eventAt) {
        PlanPrice price = prices.planOf(state.priceId()).orElseThrow(() -> new IllegalStateException(
                "Stripe price " + state.priceId() + " is no plan's: set it under lightmove.billing.stripe.prices"));
        WorkspaceSubscription subscription = subscriptions.findByWorkspaceId(workspaceId)
                .orElseGet(() -> WorkspaceSubscription.forStripe(workspaceId));
        Terms before = Terms.of(subscription);
        if (!subscription.followStripe(state, price, eventAt)) {
            return;
        }
        subscriptions.saveAndFlush(subscription);
        Terms after = Terms.of(subscription);
        if (after.equals(before)) {
            return;
        }
        audit.event(WorkspaceEventType.SUBSCRIPTION_CHANGED).workspace(workspaceId)
                .target("workspace", workspaceId)
                .detail("plan", after.plan().name())
                .detail("billingInterval", after.interval().name())
                .detail("seats", after.seats())
                .detail("status", after.status().name())
                .record();
        if (before.isLiveOnStripe()) {
            topUpUpgrade(workspaceId, subscription, before.plan());
        }
    }

    private void paid(UUID workspaceId, PaymentEvent.InvoicePaid paid) {
        if (paid.subscription() == null) {
            return;
        }
        follow(workspaceId, paid.subscription(), paid.createdAt());
        boolean stillFollowed = subscriptions.findByWorkspaceId(workspaceId)
                .map(subscription -> Objects.equals(subscription.getStripeSubscriptionId(), paid.subscriptionId()))
                .orElse(false);
        if (paid.startsPeriod() && stillFollowed) {
            grantPeriod(workspaceId, paid.subscription());
        }
    }

    /** The month of credits a paid period opens with, sized by the invoice's own plan and seats. */
    private void grantPeriod(UUID workspaceId, StripeSubscriptionState state) {
        BillingPlan plan = planOf(prices.planOf(state.priceId()).orElseThrow().plan());
        long credits = state.seats() * plan.getContactCreditsPerSeat();
        if (credits <= 0) {
            return;
        }
        BillingMonth month = BillingMonth.containing(state.periodStart(), state.periodStart());
        ledger.grant(new CreditGrantCommand(workspaceId, CreditGrantSource.PLAN, credits, month.start(), month.end(),
                BigDecimal.ZERO, MonthlyCreditReset.grantKey(workspaceId, month.start()), null, null));
    }

    /**
     * An upgrade's month is topped up to the new plan's credits at once; a downgrade takes nothing back, and the
     * smaller month starts at the next reset. Keyed on the month and the plan, so each upgrade tops up once.
     */
    private void topUpUpgrade(UUID workspaceId, WorkspaceSubscription subscription, PlanCode previous) {
        BillingPlan plan = planOf(subscription.getPlanCode());
        if (previous == plan.getCode() || plan.getContactCreditsPerSeat() <= planOf(previous).getContactCreditsPerSeat()) {
            return;
        }
        Instant now = clock.instant();
        BillingMonth month = BillingMonth.of(subscription, now);
        long owed = subscription.monthlyContactCredits(plan) - grants.monthlyCreditsOf(workspaceId, now).granted();
        if (owed <= 0) {
            return;
        }
        ledger.grant(new CreditGrantCommand(workspaceId, CreditGrantSource.PLAN, owed, now, month.end(),
                BigDecimal.ZERO, "upgrade:" + workspaceId + ":" + month.start().truncatedTo(ChronoUnit.SECONDS) + ":"
                + plan.getCode(), null, "Upgraded to " + plan.getName()));
    }

    private void failed(UUID workspaceId, PaymentEvent.InvoicePaymentFailed failed) {
        subscriptions.findByWorkspaceId(workspaceId)
                .filter(subscription -> subscription.markPastDue(failed.subscriptionId(), failed.createdAt()))
                .ifPresent(subscription -> {
                    subscriptions.saveAndFlush(subscription);
                    audit.event(WorkspaceEventType.SUBSCRIPTION_CHANGED).workspace(workspaceId)
                            .target("workspace", workspaceId)
                            .detail("status", SubscriptionStatus.PAST_DUE.name())
                            .record();
                });
    }

    /** Bought credits are spent last and kept a year, valued at what was paid for them before VAT. */
    private void credit(UUID workspaceId, PaymentEvent.CreditsPaid bought) {
        if (bought.credits() <= 0) {
            log.warn("Stripe event {} paid for a pack of {} credits; nothing granted", bought.eventId(), bought.credits());
            return;
        }
        Instant now = clock.instant();
        BigDecimal perCredit = BigDecimal.valueOf(bought.netFils())
                .divide(BigDecimal.valueOf(bought.credits()), 6, RoundingMode.HALF_UP);
        CreditGrantReceipt receipt = ledger.grant(new CreditGrantCommand(workspaceId, CreditGrantSource.PURCHASED,
                bought.credits(), now, now.atZone(ZoneOffset.UTC).plusMonths(PURCHASED_CREDITS_MONTHS).toInstant(),
                perCredit, bought.paymentRef(), null, "Pack " + bought.packCode()));
        if (receipt.alreadyGranted()) {
            return;
        }
        audit.event(WorkspaceEventType.CREDITS_PURCHASED).workspace(workspaceId)
                .target("creditGrant", receipt.grantId())
                .detail("pack", bought.packCode())
                .detail("credits", bought.credits())
                .detail("netFils", bought.netFils())
                .record();
    }

    /** A customer nobody here made — one added by hand in Stripe's dashboard — belongs to no workspace. */
    private Optional<UUID> workspaceOf(String customerId, PaymentEvent event) {
        Optional<UUID> workspaceId = customerId == null ? Optional.empty() : customers.workspaceOf(customerId);
        if (workspaceId.isEmpty()) {
            log.warn("Stripe event {} ({}) names customer {}, which is no workspace's", event.eventId(), event.type(),
                    customerId);
        }
        return workspaceId;
    }

    private BillingPlan planOf(PlanCode code) {
        return plans.findById(code).orElseThrow(() -> new IllegalStateException(
                "plan " + code + " is missing from app_lm_billing_plan"));
    }

    private record Terms(PlanCode plan, BillingInterval interval, int seats, SubscriptionStatus status,
                         boolean isLiveOnStripe) {

        static Terms of(WorkspaceSubscription subscription) {
            return new Terms(subscription.getPlanCode(), subscription.getBillingInterval(), subscription.getSeats(),
                    subscription.getStatus(), subscription.isBilledByStripe());
        }
    }
}
