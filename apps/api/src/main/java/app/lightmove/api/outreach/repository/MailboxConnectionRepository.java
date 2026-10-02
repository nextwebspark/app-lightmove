package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.MailboxConnection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Connected mailboxes. Every finder carries the workspace and the user: a mailbox is one person's. */
public interface MailboxConnectionRepository extends JpaRepository<MailboxConnection, UUID> {

    Optional<MailboxConnection> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /**
     * The one finder without a workspace: a mail service's webhook names a grant and nothing else, and
     * a grant is only ever handed to the person who connected it. A list, because one mailbox connected
     * in two workspaces can come back as one grant.
     */
    List<MailboxConnection> findByGrantId(String grantId);
}
