package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.model.MailboxConnection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Connected mailboxes. Every finder carries the workspace and the user: a mailbox is one person's. */
public interface MailboxConnectionRepository extends JpaRepository<MailboxConnection, UUID> {

    Optional<MailboxConnection> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /**
     * The one finder without a workspace: a mail service's webhook names a grant and nothing else, and
     * a grant is only ever handed to the person who connected it. A list, because one mailbox connected
     * in two workspaces can come back as one grant.
     */
    List<MailboxConnection> findByGrantId(String grantId);

    /**
     * Calendars still owed their first read and due to be tried, oldest connection first. A system job's
     * read, so across workspaces by design; no request path may use it.
     */
    @Query("select m from MailboxConnection m where m.status = :status and m.calendarSyncedAt is null "
            + "and m.calendarSyncAttempts < :maxAttempts "
            + "and (m.calendarSyncRetryAt is null or m.calendarSyncRetryAt <= :now) order by m.connectedAt")
    List<MailboxConnection> findCalendarsOwed(MailboxStatus status, int maxAttempts, Instant now, Pageable page);
}
