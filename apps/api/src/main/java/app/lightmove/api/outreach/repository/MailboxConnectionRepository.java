package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.MailboxConnection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Connected mailboxes. Every finder carries the workspace and the user: a mailbox is one person's. */
public interface MailboxConnectionRepository extends JpaRepository<MailboxConnection, UUID> {

    Optional<MailboxConnection> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
