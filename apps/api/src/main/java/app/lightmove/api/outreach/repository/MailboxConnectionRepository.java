package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.constant.MailboxGatewayKind;
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

    /** A public booking page's read: the slug is the link's whole identity, so this finder carries no workspace. */
    Optional<MailboxConnection> findByBookingSlug(String bookingSlug);

    boolean existsByBookingSlug(String bookingSlug);

    /** A booking webhook names the page and nothing else. A list, like {@link #findByGrantId}. */
    List<MailboxConnection> findByBookingConfigurationId(String bookingConfigurationId);

    /**
     * Calendars still owed their first read and due to be tried, oldest connection first. A system job's
     * read, so across workspaces by design; no request path may use it.
     */
    @Query("select m from MailboxConnection m where m.status = :status and m.calendarSyncedAt is null "
            + "and m.calendarSyncAttempts < :maxAttempts "
            + "and (m.calendarSyncRetryAt is null or m.calendarSyncRetryAt <= :now) order by m.connectedAt")
    List<MailboxConnection> findCalendarsOwed(MailboxStatus status, int maxAttempts, Instant now, Pageable page);

    List<MailboxConnection> findByWorkspaceIdAndGateway(UUID workspaceId, MailboxGatewayKind gateway);

    /** Recall's webhook names its calendar and nothing else. A list, like {@link #findByGrantId}. */
    List<MailboxConnection> findByRecallCalendarId(String recallCalendarId);

    /**
     * Direct mailboxes still owed a Recall calendar in workspaces syncing through Recall, oldest first. A system
     * job's read, across workspaces by design; no request path may use it.
     */
    @Query(value = "select m.id from app_lm_mailbox_connection m join app_lm_workspace w on w.id = m.workspace_id "
            + "where m.gateway = 'DIRECT' and m.status = 'ACTIVE' and m.recall_calendar_id is null "
            + "and w.calendar_sync = 'RECALL' order by m.connected_at limit :limit", nativeQuery = true)
    List<UUID> findOwedRecallCalendars(int limit);
}
