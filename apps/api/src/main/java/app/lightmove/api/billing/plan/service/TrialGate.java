package app.lightmove.api.billing.plan.service;

import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refuses a paid action — a contact find, search or AI — once a workspace's trial has ended unpaid, while enforcement
 * is on. A workspace with no subscription, or one billed any other way, is never refused here.
 */
@Service
@RequiredArgsConstructor
public class TrialGate {

    private final WorkspaceSubscriptionRepository subscriptions;
    private final LightMoveProperties properties;
    private final Clock clock;

    /** @throws ApiException {@code TRIAL_ENDED}, carrying {@code trialEndedAt} */
    @Transactional(readOnly = true)
    public void requireOpen(UUID workspaceId) {
        if (!properties.billing().enforce()) {
            return;
        }
        Instant now = clock.instant();
        subscriptions.findByWorkspaceId(workspaceId)
                .filter(subscription -> subscription.trialEndedAt(now))
                .ifPresent(ended -> {
                    throw ApiException.withProperty(ErrorCode.TRIAL_ENDED, "trialEndedAt", ended.getTrialEndsAt());
                });
    }
}
