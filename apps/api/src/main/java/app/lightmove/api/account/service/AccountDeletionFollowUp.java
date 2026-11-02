package app.lightmove.api.account.service;

import app.lightmove.api.account.model.AccountDeleted;
import app.lightmove.api.core.tenant.WorkspaceScope;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Once a deletion has committed, what the account held at a provider — a mailbox's grant, a Zoom account — is let go
 * there, each workspace in its own scope. A provider that cannot be reached is logged and the rest still go: the rows
 * that could spend a token were already unreachable once the membership ended. Each contributor writes in transactions
 * of its own.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class AccountDeletionFollowUp {

    private final List<AccountDeletionContributor> contributors;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onDeleted(AccountDeleted deleted) {
        for (UUID workspaceId : deleted.workspaceIds()) {
            for (AccountDeletionContributor contributor : contributors) {
                try {
                    // Joined: when the pool is full this runs on the deleting thread, whose committed connection
                    // is still bound and still reported active, and a plain scope refuses another workspace there.
                    WorkspaceScope.runInJoinedTransaction(workspaceId,
                            () -> contributor.releaseAfterAccountDeleted(workspaceId, deleted.userId()));
                } catch (RuntimeException failed) {
                    log.warn("Could not release what deleted account {} held in workspace {}",
                            deleted.userId(), workspaceId, failed);
                }
            }
        }
    }
}
