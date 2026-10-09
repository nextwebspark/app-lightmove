package app.lightmove.api.billing.trial.service;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.credit.service.CreditLedger;
import app.lightmove.api.billing.plan.model.AppTrialConverted;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.TrialSettings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Billing's door for {@code workspace}'s founding: a new workspace starts on a Pro trial with a few contact credits,
 * expiring with it. One trial per founder — a second workspace they found starts with its trial already ended, so
 * founding another is never a way to a fresh one. Two foundings by one founder at once are taken one after the other
 * under a lock on the founder, with V128's unique index behind it.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceTrials {

    private final WorkspaceSubscriptionRepository subscriptions;
    private final CreditLedger ledger;
    private final JdbcTemplate jdbc;
    private final LightMoveProperties properties;
    private final Clock clock;

    public static String grantKey(UUID workspaceId) {
        return "trial:" + workspaceId;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void start(UUID workspaceId, UUID founderId) {
        TrialSettings settings = properties.billing().trial();
        if (!settings.enabled()) {
            return;
        }
        Instant now = clock.instant();
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "trial:" + founderId);
        if (subscriptions.existsByTrialStartedBy(founderId)) {
            subscriptions.save(WorkspaceSubscription.trialAlreadySpent(workspaceId, founderId, now));
            return;
        }
        Instant endsAt = now.plus(settings.length());
        subscriptions.save(WorkspaceSubscription.trial(workspaceId, founderId, now, endsAt));
        if (settings.credits() > 0) {
            ledger.grant(new CreditGrantCommand(workspaceId, CreditGrantSource.PLAN, settings.credits(), now, endsAt,
                    BigDecimal.ZERO, grantKey(workspaceId), null, "Trial"));
        }
    }

    /**
     * A paid plan ends the trial and its credits with it, so the month's credits are the plan's alone: the allowance
     * Settings → Billing shows, the thresholds and an upgrade's top-up are all counted from them.
     */
    @EventListener
    public void endCreditsOnConversion(AppTrialConverted converted) {
        ledger.endEarly(converted.workspaceId(), CreditGrantSource.PLAN, grantKey(converted.workspaceId()));
    }
}
