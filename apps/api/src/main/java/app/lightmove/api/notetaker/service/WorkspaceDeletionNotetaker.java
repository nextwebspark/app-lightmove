package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.workspace.model.WorkspaceDeleted;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** A deleted workspace records no more calls: once the deletion commits, every bot it sent is called back. */
@Component
@RequiredArgsConstructor
class WorkspaceDeletionNotetaker {

    private final NotetakerService notetaker;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onDeleted(WorkspaceDeleted deleted) {
        WorkspaceScope.run(deleted.workspaceId(), () -> notetaker.callBackForWorkspaceDeletion(deleted.workspaceId(),
                deleted.actorUserId()));
    }
}
