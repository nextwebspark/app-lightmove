package app.lightmove.api.billing.plan.service;

import app.lightmove.api.billing.plan.model.BillingMonth;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.billing.usage.model.FairUseAllowance;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A workspace's staff seats and billing month as fair use counts them. A workspace founded after V118 has no
 * subscription yet, so its active staff are counted instead; never fewer than one seat.
 */
@Service
@RequiredArgsConstructor
public class BillingSeats {

    private final WorkspaceSubscriptionRepository subscriptions;
    private final WorkspaceAccess access;

    @Transactional(readOnly = true)
    public FairUseAllowance allowanceOf(UUID workspaceId, Instant now) {
        Optional<WorkspaceSubscription> subscription = subscriptions.findByWorkspaceId(workspaceId);
        long seats = subscription.map(WorkspaceSubscription::getSeats)
                .filter(subscribed -> subscribed > 0)
                .map(Integer::longValue)
                .orElseGet(() -> (long) access.activeStaff(workspaceId).size());
        BillingMonth month = BillingMonth.of(subscription.orElse(null), now);
        return new FairUseAllowance(Math.max(seats, 1), month.start(), month.end());
    }
}
