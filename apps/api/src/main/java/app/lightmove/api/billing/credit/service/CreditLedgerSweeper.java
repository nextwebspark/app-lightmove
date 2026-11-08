package app.lightmove.api.billing.credit.service;

import app.lightmove.api.core.tenant.EveryWorkspace;
import app.lightmove.api.core.tenant.TenantResolvers;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires lapsed grants and releases holds a dead worker never settled, a workspace at a time through
 * {@link CreditLedger#settleLapsed}. Its lock and status checks make two instances sweeping at once harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditLedgerSweeper {

    private static final int WORKSPACES_PER_SWEEP = 500;

    private final TenantResolvers resolvers;
    private final CreditLedger ledger;
    private final EveryWorkspace everyWorkspace;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${lightmove.billing.sweep-interval}",
            initialDelayString = "${lightmove.billing.sweep-interval}")
    public void scheduledSweep() {
        sweepAt(clock.instant());
    }

    /** A workspace at a time, in its own scope: the holds, grants and balances are under row-level security. */
    public void sweepAt(Instant now) {
        int settled = everyWorkspace.sum(resolvers.workspacesWithLapsedCredits(now, WORKSPACES_PER_SWEEP),
                workspaceId -> {
                    ledger.settleLapsed(workspaceId);
                    return 1;
                });
        if (settled > 0) {
            log.info("Settled lapsed credits in {} workspaces", settled);
        }
    }
}
