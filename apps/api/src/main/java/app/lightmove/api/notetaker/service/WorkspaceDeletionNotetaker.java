package app.lightmove.api.notetaker.service;

import app.lightmove.api.workspace.model.WorkspaceDeleted;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A deleted workspace records no more calls. Heard inside the deletion's transaction, so its bots are called off and
 * queued with it, never on a promise kept after the commit.
 */
@Component
@RequiredArgsConstructor
class WorkspaceDeletionNotetaker {

    private final NotetakerService notetaker;

    @EventListener
    void onDeleted(WorkspaceDeleted deleted) {
        notetaker.callBackForWorkspaceDeletion(deleted.workspaceId(), deleted.actorUserId());
    }
}
