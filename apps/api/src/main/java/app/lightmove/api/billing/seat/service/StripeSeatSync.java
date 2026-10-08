package app.lightmove.api.billing.seat.service;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.credit.service.CreditLedger;
import app.lightmove.api.billing.payment.model.SeatQuantityChange;
import app.lightmove.api.billing.payment.service.PaymentGateway;
import app.lightmove.api.billing.plan.model.BillingMonth;
import app.lightmove.api.billing.plan.model.BillingPlan;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.BillingPlanRepository;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.billing.seat.model.StaffSeatsChanged;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps a Stripe subscription's quantity at its workspace's staff seats: asked after the membership change commits,
 * never inside it, and retried by its own job while Stripe refuses. A seat added mid-month brings its share of the
 * month's credits at once, once per seat number and month; a removed seat takes nothing back.
 */
@Slf4j
@Component
public class StripeSeatSync {

    /** A fresh request is the after-commit listener's; the job leaves it this long before trying it again. */
    static final Duration RETRY_AFTER = Duration.ofMinutes(1);

    private static final int WORKSPACES_PER_RUN = 100;

    private final WorkspaceSubscriptionRepository subscriptions;
    private final BillingPlanRepository plans;
    private final WorkspaceAccess access;
    private final PaymentGateway gateway;
    private final CreditLedger ledger;
    private final AuditService audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /** Its own transactions, never the caller's: the membership's has committed, and Stripe is called outside any. */
    StripeSeatSync(WorkspaceSubscriptionRepository subscriptions, BillingPlanRepository plans, WorkspaceAccess access,
                   PaymentGateway gateway, CreditLedger ledger, AuditService audit,
                   PlatformTransactionManager transactionManager, Clock clock) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.access = access;
        this.gateway = gateway;
        this.ledger = ledger;
        this.audit = audit;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public static String seatGrantKey(UUID workspaceId, Instant monthStart, long seat) {
        return "seat:" + workspaceId + ":" + monthStart.truncatedTo(ChronoUnit.SECONDS) + ":" + seat;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onStaffSeatsChanged(StaffSeatsChanged changed) {
        syncQuietly(changed.workspaceId());
    }

    @Scheduled(cron = "${lightmove.billing.jobs.seat-sync}", zone = "UTC")
    public void scheduledSync() {
        syncDueAt(clock.instant());
    }

    /** @return how many workspaces Stripe now holds the right quantity for */
    public int syncDueAt(Instant now) {
        int synced = 0;
        for (UUID workspaceId : subscriptions.findWorkspacesOwingSeatSync(now.minus(RETRY_AFTER), WORKSPACES_PER_RUN)) {
            synced += syncQuietly(workspaceId) ? 1 : 0;
        }
        return synced;
    }

    private boolean syncQuietly(UUID workspaceId) {
        try {
            return sync(workspaceId);
        } catch (RuntimeException failure) {
            log.warn("Could not sync the Stripe seats of workspace {}; the job tries again", workspaceId, failure);
            return false;
        }
    }

    /**
     * Read in a transaction of its own: after a commit the finished one's persistence context is still bound, holding
     * the row as it was before the mark. Re-counts after Stripe answers: a change committed while it was asked leaves
     * the request standing, so a slower sync of an older count is never the last word.
     */
    private boolean sync(UUID workspaceId) {
        WorkspaceSubscription subscription =
                transactions.execute(status -> subscriptions.findByWorkspaceId(workspaceId).orElse(null));
        if (subscription == null || subscription.getSeatSyncDueAt() == null) {
            return false;
        }
        Instant dueAt = subscription.getSeatSyncDueAt();
        if (!subscription.isBilledByStripe()) {
            transactions.executeWithoutResult(status -> subscriptions.clearSeatSync(workspaceId, dueAt));
            return false;
        }
        long seats = staffSeatsOf(workspaceId);
        SeatQuantityChange change = gateway.updateSeats(subscription.getStripeSubscriptionId(), seats);
        transactions.executeWithoutResult(status -> {
            grantAddedSeats(subscription, change);
            subscriptions.clearSeatSync(workspaceId, dueAt);
            if (staffSeatsOf(workspaceId) != seats) {
                subscriptions.markSeatSyncDue(workspaceId, clock.instant());
            }
        });
        if (change.added() || change.removed()) {
            audit.event(change.added() ? WorkspaceEventType.SEAT_ADDED : WorkspaceEventType.SEAT_REMOVED)
                    .workspace(workspaceId)
                    .target("workspace", workspaceId)
                    .detail("seats", change.current())
                    .detail("previousSeats", change.previous())
                    .record();
        }
        return true;
    }

    private void grantAddedSeats(WorkspaceSubscription subscription, SeatQuantityChange change) {
        if (!change.added()) {
            return;
        }
        BillingPlan plan = plans.findById(subscription.getPlanCode()).orElseThrow(() -> new IllegalStateException(
                "plan " + subscription.getPlanCode() + " is missing from app_lm_billing_plan"));
        Instant now = clock.instant();
        BillingMonth month = BillingMonth.of(subscription, now);
        long credits = creditsLeftIn(month, plan.getContactCreditsPerSeat(), now);
        if (credits <= 0) {
            return;
        }
        UUID workspaceId = subscription.getWorkspaceId();
        for (long seat = change.previous() + 1; seat <= change.current(); seat++) {
            ledger.grant(new CreditGrantCommand(workspaceId, CreditGrantSource.PLAN, credits, now, month.end(),
                    BigDecimal.ZERO, seatGrantKey(workspaceId, month.start(), seat), null, "Seat " + seat));
        }
    }

    static long creditsLeftIn(BillingMonth month, Integer perSeat, Instant now) {
        if (perSeat == null || !now.isBefore(month.end())) {
            return 0;
        }
        long left = Duration.between(now, month.end()).toSeconds();
        long whole = Duration.between(month.start(), month.end()).toSeconds();
        return BigDecimal.valueOf(perSeat).multiply(BigDecimal.valueOf(left))
                .divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_UP).longValue();
    }

    private long staffSeatsOf(UUID workspaceId) {
        return Math.max(1, access.activeStaff(workspaceId).size());
    }
}
