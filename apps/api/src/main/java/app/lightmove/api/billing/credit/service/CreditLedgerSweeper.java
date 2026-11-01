package app.lightmove.api.billing.credit.service;

import app.lightmove.api.billing.credit.repository.CreditHoldRepository;
import app.lightmove.api.core.tenant.EveryWorkspace;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

    private final CreditHoldRepository holds;
    private final CreditLedger ledger;
    private final EveryWorkspace everyWorkspace;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${lightmove.billing.sweep-interval}",
            initialDelayString = "${lightmove.billing.sweep-interval}")
    public void scheduledSweep() {
        sweepAt(clock.instant());
    }

    /** A workspace at a time: the holds, grants and balances are under row-level security. */
    public void sweepAt(Instant now) {
        int settled = everyWorkspace.sum(scoped -> {
            List<UUID> workspaces = holds.findWorkspacesWithLapsedCredits(now, WORKSPACES_PER_SWEEP);
            for (UUID workspaceId : workspaces) {
                try {
                    ledger.settleLapsed(workspaceId);
                } catch (RuntimeException failure) {
                    log.error("Could not settle lapsed credits of workspace {}", workspaceId, failure);
                }
            }
            return workspaces.size();
        });
        if (settled > 0) {
            log.info("Settled lapsed credits in {} workspaces", settled);
        }
    }
}
