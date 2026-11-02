package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.model.PersonProcessingRestricted;
import app.lightmove.api.core.tenant.WorkspaceScope;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A restricted person is recorded no further: once the restriction commits, their bots are called back. Off the
 * request thread, whose finished transaction a write here would otherwise join and vanish with, and in their workspace.
 */
@Component
@RequiredArgsConstructor
class ProcessingRestrictionNotetaker {

    private final NotetakerService notetaker;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onRestricted(PersonProcessingRestricted restricted) {
        WorkspaceScope.run(restricted.workspaceId(), () -> notetaker.callBackForRestriction(restricted.workspaceId(),
                restricted.personId(), restricted.restrictedBy()));
    }
}
