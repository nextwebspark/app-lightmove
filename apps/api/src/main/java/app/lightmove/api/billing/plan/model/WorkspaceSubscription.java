package app.lightmove.api.billing.plan.model;

import app.lightmove.api.billing.payment.model.PlanPrice;
import app.lightmove.api.billing.payment.model.StripeSubscriptionState;
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

/** A workspace's plan, staff seats and status (V118); one per workspace. */
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

    public boolean isBilledByStripe() {
        return stripeSubscriptionId != null && status != SubscriptionStatus.CANCELLED;
    }

    /**
     * Takes Stripe's word on the plan, seats, period and status. Changes nothing, and answers false, while Stripe still
     * waits for a first payment, for news older than the event the row last followed, or for a subscription other
     * than the live one, such as a late event about one cancelled since.
     */
    public boolean followStripe(StripeSubscriptionState state, PlanPrice price, Instant eventAt) {
        if (state.status() == null || !follows(state.subscriptionId(), eventAt)) {
            return false;
        }
        this.planCode = price.plan();
        this.billingInterval = price.interval();
        this.seats = Math.toIntExact(state.seats());
        this.contactCreditPool = null;
        this.stripeCustomerId = state.customerId();
        this.stripeSubscriptionId = state.subscriptionId();
        this.currentPeriodStart = state.periodStart();
        this.currentPeriodEnd = state.periodEnd();
        moveTo(state.status(), eventAt);
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
