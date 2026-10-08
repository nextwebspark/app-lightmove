package app.lightmove.api.billing.payment.service;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.credit.model.CreditGrantReceipt;
import app.lightmove.api.billing.credit.repository.CreditGrantRepository;
import app.lightmove.api.billing.credit.service.CreditLedger;
import app.lightmove.api.billing.credit.service.MonthlyCreditReset;
import app.lightmove.api.billing.payment.model.BillingCustomer;
import app.lightmove.api.billing.payment.model.PaymentEvent;
import app.lightmove.api.billing.payment.model.PlanPrice;
import app.lightmove.api.billing.payment.model.StripeSubscriptionState;
import app.lightmove.api.billing.payment.repository.BillingCustomerRepository;
import app.lightmove.api.billing.payment.repository.BillingWebhookEventRepository;
import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.PlanCode;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import app.lightmove.api.billing.plan.model.BillingMonth;
import app.lightmove.api.billing.plan.model.BillingPlan;
import app.lightmove.api.billing.plan.model.PaidSubscription;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.BillingPlanRepository;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.CreditPackSettings;
import app.lightmove.api.core.config.LightMoveProperties;
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
 * Turns verified Stripe events into the subscription's state and contact-credit grants, each claimed and handled in
 * one transaction. A paid period's month is granted under {@link MonthlyCreditReset#grantKey}, so the job and the
 * webhook never both grant it. An event this deployment cannot place is claimed and ignored, never retried.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeEventHandler {

    private static final int PURCHASED_CREDITS_MONTHS = 12;

    private final BillingWebhookEventRepository events;
    private final BillingCustomerRepository customers;
    private final WorkspaceSubscriptionRepository subscriptions;
    private final BillingPlanRepository plans;
    private final CreditGrantRepository grants;
    private final PlanPrices prices;
    private final CreditLedger ledger;
    private final AuditService audit;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Transactional
    public void handle(PaymentEvent event) {
        if (event instanceof PaymentEvent.Ignored || events.claim(event.eventId(), event.type()) == 0) {
            return;
        }
        switch (event) {
            case PaymentEvent.SubscriptionChanged changed -> workspaceOf(changed.subscription().customerId(), event)
                    .ifPresent(workspaceId -> paidOf(changed.subscription(), event)
                            .ifPresent(paid -> follow(workspaceId, paid, event.createdAt())));
            case PaymentEvent.InvoicePaid paid ->
                    workspaceOf(paid.customerId(), event).ifPresent(workspaceId -> invoicePaid(workspaceId, paid));
            case PaymentEvent.InvoicePaymentFailed failed ->
                    workspaceOf(failed.customerId(), event).ifPresent(workspaceId -> paymentFailed(workspaceId, failed));
            case PaymentEvent.CreditsPaid bought ->
                    workspaceOf(bought.customerId(), event).ifPresent(workspaceId -> credit(workspaceId, bought));
            case PaymentEvent.Ignored ignored -> {
            }
        }
    }

    private void follow(UUID workspaceId, PaidSubscription paid, Instant eventAt) {
        WorkspaceSubscription subscription = subscriptions.findByWorkspaceId(workspaceId)
                .orElseGet(() -> WorkspaceSubscription.forStripe(workspaceId));
        Terms before = Terms.of(subscription);
        if (!subscription.followStripe(paid, eventAt)) {
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
        if (before.liveOnStripe()) {
            topUpUpgrade(workspaceId, subscription, before.plan());
        }
    }

    private void invoicePaid(UUID workspaceId, PaymentEvent.InvoicePaid invoice) {
        if (invoice.subscription() == null) {
            return;
        }
        Optional<PaidSubscription> paid = paidOf(invoice.subscription(), invoice);
        if (paid.isEmpty()) {
            return;
        }
        follow(workspaceId, paid.get(), invoice.createdAt());
        boolean stillFollowed = subscriptions.findByWorkspaceId(workspaceId)
                .map(subscription -> Objects.equals(subscription.getStripeSubscriptionId(), invoice.subscriptionId()))
                .orElse(false);
        if (invoice.startsPeriod() && stillFollowed) {
            grantPeriod(workspaceId, paid.get());
        }
    }

    /** The month of credits a paid period opens with, sized by the invoice's own plan and seats. */
    private void grantPeriod(UUID workspaceId, PaidSubscription paid) {
        long credits = (long) paid.seats() * planOf(paid.plan()).getContactCreditsPerSeat();
        if (credits <= 0) {
            return;
        }
        BillingMonth month = BillingMonth.containing(paid.periodStart(), paid.periodStart());
        ledger.grant(new CreditGrantCommand(workspaceId, CreditGrantSource.PLAN, credits, month.start(), month.end(),
                BigDecimal.ZERO, MonthlyCreditReset.grantKey(workspaceId, month.start()), null, null));
    }

    /**
     * An upgrade tops the month up to the new plan's credits at once, once per month and plan; a downgrade takes
     * nothing back.
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

    private void paymentFailed(UUID workspaceId, PaymentEvent.InvoicePaymentFailed failed) {
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
        CreditPackSettings pack = properties.billing().packs().get(bought.packCode());
        if (pack == null || pack.credits() <= 0) {
            log.warn("Stripe event {} paid for pack {}, which is not configured; nothing granted", bought.eventId(),
                    bought.packCode());
            return;
        }
        Instant now = clock.instant();
        BigDecimal perCredit = BigDecimal.valueOf(bought.netFils())
                .divide(BigDecimal.valueOf(pack.credits()), 6, RoundingMode.HALF_UP);
        CreditGrantReceipt receipt = ledger.grant(new CreditGrantCommand(workspaceId, CreditGrantSource.PURCHASED,
                pack.credits(), now, now.atZone(ZoneOffset.UTC).plusMonths(PURCHASED_CREDITS_MONTHS).toInstant(),
                perCredit, bought.paymentRef(), null, "Pack " + bought.packCode()));
        if (receipt.alreadyGranted()) {
            return;
        }
        audit.event(WorkspaceEventType.CREDITS_PURCHASED).workspace(workspaceId)
                .target("creditGrant", receipt.grantId())
                .detail("pack", bought.packCode())
                .detail("credits", pack.credits())
                .detail("netFils", bought.netFils())
                .record();
    }

    /**
     * Empty while Stripe still waits for a first payment, and for a price no plan is configured with — one belonging
     * to another product on the same Stripe account, say.
     */
    private Optional<PaidSubscription> paidOf(StripeSubscriptionState state, PaymentEvent event) {
        if (state.status() == null) {
            return Optional.empty();
        }
        Optional<PlanPrice> price = prices.planOf(state.priceId());
        if (price.isEmpty()) {
            log.warn("Stripe event {} ({}) is for price {}, which no plan is configured with; ignored",
                    event.eventId(), event.type(), state.priceId());
            return Optional.empty();
        }
        return Optional.of(new PaidSubscription(state.customerId(), state.subscriptionId(), price.get().plan(),
                price.get().interval(), Math.toIntExact(state.seats()), state.status(), state.periodStart(),
                state.periodEnd()));
    }

    /** A customer nobody here made, such as one added by hand in Stripe's dashboard, belongs to no workspace. */
    private Optional<UUID> workspaceOf(String customerId, PaymentEvent event) {
        Optional<UUID> workspaceId = customerId == null ? Optional.empty()
                : customers.findByStripeCustomerId(customerId).map(BillingCustomer::getWorkspaceId);
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
                         boolean liveOnStripe) {

        static Terms of(WorkspaceSubscription subscription) {
            return new Terms(subscription.getPlanCode(), subscription.getBillingInterval(), subscription.getSeats(),
                    subscription.getStatus(), subscription.isBilledByStripe());
        }
    }
}
