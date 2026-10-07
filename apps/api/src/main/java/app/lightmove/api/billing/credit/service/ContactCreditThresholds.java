package app.lightmove.api.billing.credit.service;

import app.lightmove.api.billing.credit.constant.ContactCreditLevel;
import app.lightmove.api.billing.credit.model.ContactCreditThresholdCrossed;
import app.lightmove.api.billing.credit.model.MonthlyCredits;
import app.lightmove.api.billing.credit.repository.CreditGrantRepository;
import app.lightmove.api.billing.plan.model.BillingMonth;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Claims each threshold a spend reached, once per workspace and billing month, and announces the highest one newly
 * claimed. Runs inside the ledger's transaction, under its balance lock: a rolled-back spend claims nothing.
 */
@Component
@RequiredArgsConstructor
class ContactCreditThresholds {

    private final CreditGrantRepository grants;
    private final WorkspaceSubscriptionRepository subscriptions;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    void claimCrossed(UUID workspaceId, long available, Instant now) {
        MonthlyCredits monthly = grants.monthlyCreditsOf(workspaceId, now);
        ContactCreditLevel level = monthly.levelAt(available);
        if (level == ContactCreditLevel.OK) {
            return;
        }
        BillingMonth month = subscriptions.findByWorkspaceId(workspaceId)
                .map(subscription -> subscription.monthContaining(now))
                .orElseGet(() -> BillingMonth.containing(null, now));
        ContactCreditLevel announced = null;
        for (ContactCreditLevel threshold : ContactCreditLevel.values()) {
            if (threshold != ContactCreditLevel.OK && threshold.compareTo(level) <= 0
                    && claim(workspaceId, month, threshold, now)) {
                announced = threshold;
            }
        }
        if (announced != null) {
            events.publishEvent(new ContactCreditThresholdCrossed(workspaceId, announced, month.start(), month.end(),
                    monthly.granted(), available));
        }
    }

    private boolean claim(UUID workspaceId, BillingMonth month, ContactCreditLevel threshold, Instant now) {
        return jdbc.update("""
                INSERT INTO app_lm_credit_threshold_crossing (workspace_id, month_start, threshold, crossed_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING""",
                workspaceId, Timestamp.from(month.start()), threshold.name(), Timestamp.from(now)) == 1;
    }
}
