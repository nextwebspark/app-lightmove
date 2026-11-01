package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.model.MailboxConnection;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Connected mailboxes. Every finder carries the workspace and the user: a mailbox is one person's. */
public interface MailboxConnectionRepository extends JpaRepository<MailboxConnection, UUID> {

    Optional<MailboxConnection> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /** One person's mailbox in the workspace in scope; their profile's clock reaches each workspace's in its own. */
    List<MailboxConnection> findByUserId(UUID userId);

    /**
     * A grant's mailbox in the workspace in scope: a mail service's webhook names a grant and nothing else, so its
     * workspace is resolved first ({@code TenantResolvers}). A list, because one mailbox connected in two workspaces
     * can come back as one grant.
     */
    List<MailboxConnection> findByGrantId(String grantId);

    boolean existsByGrantIdAndStatus(String grantId, MailboxStatus status);

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
     * Direct mailboxes at a host Recall reads, still owed a Recall calendar and due to be tried, in workspaces
     * syncing through Recall, oldest first. A system job's read, across workspaces by design; no request path may
     * use it.
     */
    @Query(value = "select m.* from app_lm_mailbox_connection m join app_lm_workspace w on w.id = m.workspace_id "
            + "where m.gateway = 'DIRECT' and m.status = 'ACTIVE' and m.recall_calendar_id is null "
            + "and m.provider in ('google', 'microsoft') and m.recall_calendar_attempts < :maxAttempts "
            + "and (m.recall_calendar_retry_at is null or m.recall_calendar_retry_at <= :now) "
            + "and w.calendar_sync = 'RECALL' order by m.connected_at limit :limit", nativeQuery = true)
    List<MailboxConnection> findOwedRecallCalendars(int maxAttempts, Instant now, int limit);

    /** Live direct mailboxes made through {@code clientId} at a provider; a row from before V168 counts as made through it. */
    @Query("select count(m) from MailboxConnection m where m.workspaceId = :workspaceId and m.gateway = :gateway "
            + "and m.status = :status and lower(m.provider) = :provider "
            + "and (m.appClientId is null or m.appClientId = :clientId)")
    long countLiveThrough(UUID workspaceId, MailboxGatewayKind gateway, MailboxStatus status, String provider,
                          String clientId);

    /**
     * Withdraws the still-live rows among {@code ids} and lets go of their Recall calendars, in one statement: dirtying
     * each versioned row would fail the admin's whole save on any concurrent write to one of them (a rotated refresh
     * token, a Recall calendar kept).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MailboxConnection m set m.status = :withdrawn, m.recallCalendarId = null "
            + "where m.id in :ids and m.status = :live")
    int withdrawLive(Collection<UUID> ids, MailboxStatus live, MailboxStatus withdrawn);
}
