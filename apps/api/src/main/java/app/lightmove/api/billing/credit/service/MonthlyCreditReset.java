package app.lightmove.api.billing.credit.service;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.credit.repository.CreditGrantRepository;
import app.lightmove.api.billing.plan.model.BillingMonth;
import app.lightmove.api.billing.plan.model.BillingPlan;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.BillingPlanRepository;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Grants each workspace on a live plan its month's contact credits, expiring with the month: no rollover, since the
 * ledger expires the old month's grant before it writes the new one. Keyed {@code plan:<workspace>:<month start>},
 * the key Stripe's {@code invoice.paid} grants under, so a webhook and this job never both grant a month, and two
 * instances running it at once grant it once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyCreditReset {

    private static final int WORKSPACES_PER_RUN = 500;

    private final CreditGrantRepository grants;
    private final WorkspaceSubscriptionRepository subscriptions;
    private final BillingPlanRepository plans;
    private final CreditLedger ledger;
    private final Clock clock;

    public static String grantKey(UUID workspaceId, Instant monthStart) {
        return "plan:" + workspaceId + ":" + monthStart;
    }

    @Scheduled(cron = "${lightmove.billing.jobs.monthly-reset}", zone = "UTC")
    public void scheduledReset() {
        resetAt(clock.instant());
    }

    /** @return how many workspaces were granted a month */
    public int resetAt(Instant now) {
        List<UUID> due = grants.findWorkspacesDueMonthlyCredits(now, WORKSPACES_PER_RUN);
        int granted = 0;
        for (UUID workspaceId : due) {
            try {
                granted += grantMonth(workspaceId, now) ? 1 : 0;
            } catch (RuntimeException failure) {
                log.error("Could not grant the month's contact credits of workspace {}", workspaceId, failure);
            }
        }
        if (granted > 0) {
            log.info("Granted the month's contact credits to {} workspaces", granted);
        }
        return granted;
    }

    private boolean grantMonth(UUID workspaceId, Instant now) {
        WorkspaceSubscription subscription = subscriptions.findByWorkspaceId(workspaceId).orElseThrow();
        BillingPlan plan = plans.findById(subscription.getPlanCode()).orElseThrow();
        long credits = subscription.monthlyContactCredits(plan);
        if (credits <= 0) {
            return false;
        }
        BillingMonth month = subscription.monthContaining(now);
        return !ledger.grant(new CreditGrantCommand(workspaceId, CreditGrantSource.PLAN, credits, month.start(),
                month.end(), BigDecimal.ZERO, grantKey(workspaceId, month.start()), null, null)).alreadyGranted();
    }
}
