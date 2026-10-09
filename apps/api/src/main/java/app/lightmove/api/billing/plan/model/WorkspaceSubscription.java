package app.lightmove.api.billing.plan.model;

import app.lightmove.api.billing.plan.constant.BillingInterval;
import app.lightmove.api.billing.plan.constant.PlanCode;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A workspace's plan, staff seats and status (V121); one per workspace. */
@Entity
@Table(name = "app_lm_workspace_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceSubscription extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false, length = 16)
    private PlanCode planCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 16)
    private BillingInterval billingInterval;

    @Column(name = "seats", nullable = false)
    private int seats;

    /** Agreed per workspace on a custom plan; null elsewhere, where the pool is seats × the plan's credits per seat. */
    @Column(name = "contact_credit_pool")
    private Integer contactCreditPool;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SubscriptionStatus status;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "stripe_customer_id", length = 64)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 64)
    private String stripeSubscriptionId;

    @Column(name = "tax_registration_number", length = 32)
    private String taxRegistrationNumber;

    @Column(name = "past_due_since")
    private Instant pastDueSince;

    @Column(name = "stripe_synced_at")
    private Instant stripeSyncedAt;

    /** Set on a trial the app started (V128); Stripe's own trialing status never writes it. */
    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "trial_started_by", updatable = false)
    private UUID trialStartedBy;

    /** Set only on the trial that ran (V129), never cleared: a founder's later workspaces leave it null. */
    @Column(name = "trial_started_at", updatable = false)
    private Instant trialStartedAt;

    /** Written only by {@code WorkspaceSubscriptionRepository}'s seat-sync queries, never by saving the row. */
    @Column(name = "seat_sync_due_at", insertable = false, updatable = false)
    private Instant seatSyncDueAt;

    public static WorkspaceSubscription invoiced(UUID workspaceId) {
        WorkspaceSubscription subscription = new WorkspaceSubscription();
        subscription.workspaceId = workspaceId;
        subscription.status = SubscriptionStatus.INVOICED;
        return subscription;
    }

    /** A row a first Stripe event makes for a workspace that subscribed with no plan before. */
    public static WorkspaceSubscription forStripe(UUID workspaceId) {
        WorkspaceSubscription subscription = new WorkspaceSubscription();
        subscription.workspaceId = workspaceId;
        return subscription;
    }

    /**
     * Pro until {@code endsAt}, with no seats of its own: fair use counts the workspace's staff, and no month of plan
     * credits is granted. {@code trialStartedAt} marks it as the one that ran (V129's unique index, the end notices).
     */
    public static WorkspaceSubscription trial(UUID workspaceId, UUID founderId, Instant startsAt, Instant endsAt) {
        WorkspaceSubscription subscription = trialEndedAt(workspaceId, founderId, endsAt);
        subscription.trialStartedAt = startsAt;
        subscription.currentPeriodStart = startsAt;
        subscription.currentPeriodEnd = endsAt;
        return subscription;
    }

    /**
     * A founder's later workspace: its trial already over. No {@code trialStartedAt}, deliberately: that keeps it out
     * of {@code WorkspaceSubscriptionRepository.findAppTrialsEndingBetween}, so nobody is told a trial they never had
     * has ended, and out of the one-trial-per-founder index however it is later paid for.
     */
    public static WorkspaceSubscription trialAlreadySpent(UUID workspaceId, UUID founderId, Instant now) {
        return trialEndedAt(workspaceId, founderId, now);
    }

    private static WorkspaceSubscription trialEndedAt(UUID workspaceId, UUID founderId, Instant endsAt) {
        WorkspaceSubscription subscription = new WorkspaceSubscription();
        subscription.workspaceId = workspaceId;
        subscription.planCode = PlanCode.PRO;
        subscription.billingInterval = BillingInterval.MONTHLY;
        subscription.status = SubscriptionStatus.TRIALING;
        subscription.trialStartedBy = founderId;
        subscription.trialEndsAt = endsAt;
        return subscription;
    }

    /** On a trial the app started and nobody has paid to leave. */
    public boolean isAppTrial() {
        return status == SubscriptionStatus.TRIALING && stripeSubscriptionId == null && trialEndsAt != null;
    }

    public boolean trialEndedAt(Instant now) {
        return isAppTrial() && !now.isBefore(trialEndsAt);
    }

    public boolean isBilledByStripe() {
        return stripeSubscriptionId != null && status != SubscriptionStatus.CANCELLED;
    }

    /**
     * Takes Stripe's word on the subscription. False, changing nothing, for news older than the event last followed
     * or about a subscription other than the live one.
     */
    public boolean followStripe(PaidSubscription paid, Instant eventAt) {
        if (!follows(paid.subscriptionId(), eventAt)) {
            return false;
        }
        this.planCode = paid.plan();
        this.billingInterval = paid.interval();
        this.seats = paid.seats();
        this.contactCreditPool = null;
        this.stripeCustomerId = paid.customerId();
        this.stripeSubscriptionId = paid.subscriptionId();
        this.currentPeriodStart = paid.periodStart();
        this.currentPeriodEnd = paid.periodEnd();
        moveTo(paid.status(), eventAt);
        return true;
    }

    public boolean markPastDue(String subscriptionId, Instant eventAt) {
        if (stripeSubscriptionId == null || status == SubscriptionStatus.CANCELLED || !follows(subscriptionId, eventAt)) {
            return false;
        }
        moveTo(SubscriptionStatus.PAST_DUE, eventAt);
        return true;
    }

    private boolean follows(String subscriptionId, Instant eventAt) {
        if (stripeSubscriptionId != null && !stripeSubscriptionId.equals(subscriptionId)) {
            return status == SubscriptionStatus.CANCELLED;
        }
        return stripeSyncedAt == null || !eventAt.isBefore(stripeSyncedAt);
    }

    private void moveTo(SubscriptionStatus next, Instant eventAt) {
        if (next != SubscriptionStatus.PAST_DUE) {
            pastDueSince = null;
        } else if (status != SubscriptionStatus.PAST_DUE || pastDueSince == null) {
            pastDueSince = eventAt;
        }
        this.status = next;
        this.stripeSyncedAt = eventAt;
    }

    public void invoice(BillingPlan plan, BillingInterval interval, int seats, Integer contactCreditPool,
                        Instant periodStart, Instant periodEnd) {
        this.planCode = plan.getCode();
        this.billingInterval = interval;
        this.seats = seats;
        this.contactCreditPool = plan.isCustom() ? contactCreditPool : null;
        this.status = SubscriptionStatus.INVOICED;
        this.stripeSubscriptionId = null;
        this.stripeSyncedAt = null;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
    }

    public long monthlyContactCredits(BillingPlan plan) {
        if (plan.isCustom()) {
            return contactCreditPool == null ? 0 : contactCreditPool;
        }
        return (long) seats * plan.getContactCreditsPerSeat();
    }
}
