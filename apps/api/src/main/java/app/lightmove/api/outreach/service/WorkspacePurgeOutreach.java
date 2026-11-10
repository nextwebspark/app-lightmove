package app.lightmove.api.outreach.service;

import app.lightmove.api.workspace.service.WorkspacePurgeContributor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The deletion lets every mailbox and Zoom account go once it commits; one whose provider could not be reached then is
 * still a row — the only copy of the grant to revoke — and is let go again here. One still unreachable holds the purge
 * back, so its row is never deleted with the grant unrevoked.
 */
@Component
@RequiredArgsConstructor
class WorkspacePurgeOutreach implements WorkspacePurgeContributor {

    private final MailboxService mailboxes;
    private final ZoomService zoom;

    @Override
    public void beforePurge(UUID workspaceId) {
        int mailboxesHeld = mailboxes.disconnectAllOf(workspaceId, null);
        int zoomHeld = zoom.disconnectAllOf(workspaceId, null);
        if (mailboxesHeld + zoomHeld > 0) {
            throw new IllegalStateException(mailboxesHeld + " mailbox and " + zoomHeld
                    + " Zoom grants of deleted workspace " + workspaceId + " could not be revoked yet");
        }
    }
}
