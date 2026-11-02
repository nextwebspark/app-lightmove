package app.lightmove.api.outreach.service;

import app.lightmove.api.workspace.service.WorkspacePurgeContributor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The deletion lets every mailbox and Zoom account go once it commits; one whose provider could not be reached then is
 * let go again here, before its row — the only copy of the grant to revoke — is purged.
 */
@Component
@RequiredArgsConstructor
class WorkspacePurgeOutreach implements WorkspacePurgeContributor {

    private final MailboxService mailboxes;
    private final ZoomService zoom;

    @Override
    public void beforePurge(UUID workspaceId) {
        mailboxes.disconnectAllOf(workspaceId, null);
        zoom.disconnectAllOf(workspaceId, null);
    }
}
