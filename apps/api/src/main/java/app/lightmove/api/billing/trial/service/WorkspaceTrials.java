package app.lightmove.api.billing.trial.service;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.credit.service.CreditLedger;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.TrialSettings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Billing's door for {@code workspace}'s founding: a new workspace starts on a Pro trial with a few contact credits,
 * expiring with it. One trial per founder — a second workspace they found starts with its trial already ended, so
 * founding another is never a way to a fresh one.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceTrials {

    private final WorkspaceSubscriptionRepository subscriptions;
    private final CreditLedger ledger;
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
        if (subscriptions.existsByTrialStartedBy(founderId)) {
            subscriptions.save(WorkspaceSubscription.trial(workspaceId, founderId, now, now));
            return;
        }
        Instant endsAt = now.plus(settings.length());
        subscriptions.save(WorkspaceSubscription.trial(workspaceId, founderId, now, endsAt));
        if (settings.credits() > 0) {
            ledger.grant(new CreditGrantCommand(workspaceId, CreditGrantSource.PLAN, settings.credits(), now, endsAt,
                    BigDecimal.ZERO, grantKey(workspaceId), null, "Trial"));
        }
    }
}
