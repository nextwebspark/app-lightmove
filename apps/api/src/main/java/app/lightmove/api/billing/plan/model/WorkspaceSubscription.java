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

    public static WorkspaceSubscription invoiced(UUID workspaceId) {
        WorkspaceSubscription subscription = new WorkspaceSubscription();
        subscription.workspaceId = workspaceId;
        subscription.status = SubscriptionStatus.INVOICED;
        return subscription;
    }

    public boolean isBilledByStripe() {
        return stripeSubscriptionId != null;
    }

    public void invoice(BillingPlan plan, BillingInterval interval, int seats, Integer contactCreditPool,
                        Instant periodStart, Instant periodEnd) {
        this.planCode = plan.getCode();
        this.billingInterval = interval;
        this.seats = seats;
        this.contactCreditPool = plan.isCustom() ? contactCreditPool : null;
        this.status = SubscriptionStatus.INVOICED;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
    }

    public BillingMonth monthContaining(Instant now) {
        return BillingMonth.containing(currentPeriodStart, now);
    }

    public long monthlyContactCredits(BillingPlan plan) {
        if (plan.isCustom()) {
            return contactCreditPool == null ? 0 : contactCreditPool;
        }
        return (long) seats * plan.getContactCreditsPerSeat();
    }
}
