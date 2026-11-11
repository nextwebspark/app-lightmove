package app.lightmove.api.dataexport.service;

import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.dataexport.model.WorkspaceExportRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Builds an export once its row has committed, off the request thread and in its own workspace's scope. */
@Component
@RequiredArgsConstructor
class WorkspaceExportWorker {

    private final WorkspaceExportService exports;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onRequested(WorkspaceExportRequested requested) {
        WorkspaceScope.run(requested.workspaceId(), () -> exports.build(requested.exportId()));
    }
}
