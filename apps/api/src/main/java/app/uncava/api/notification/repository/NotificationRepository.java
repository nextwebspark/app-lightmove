package app.uncava.api.notification.repository;

import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import app.uncava.api.notification.model.Notification;
import app.uncava.api.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * People's bells. Every read takes the recipient and the workspace: a row is one person's, in one tenant. A row
 * about an archived position is left out of the bell and its counts until the position is restored.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    String ON_LIVE_POSITION =
            "  AND (project_id IS NULL OR project_id NOT IN (" + ProjectRepository.ARCHIVED_IDS_SQL + "))\n";

    Optional<Notification> findByIdAndWorkspaceIdAndRecipientUserId(UUID id, UUID workspaceId, UUID recipientUserId);

    default Notification requireOwn(UUID id, UUID workspaceId, UUID recipientUserId) {
        return findByIdAndWorkspaceIdAndRecipientUserId(id, workspaceId, recipientUserId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** A page of the bell, newest first, after the cursor; a snoozed row is held back until its time. */
    @Query(value = """
            SELECT * FROM app_lm_notification
            WHERE recipient_user_id = :recipientUserId AND workspace_id = :workspaceId AND archived_at IS NULL
              AND (snoozed_until IS NULL OR snoozed_until <= :now)
              AND (:unreadOnly = false OR read_at IS NULL)
              AND (latest_at, id) < (:beforeAt, :beforeId)
            """ + ON_LIVE_POSITION + """
            ORDER BY latest_at DESC, id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Notification> findInbox(UUID recipientUserId, UUID workspaceId, Instant now, boolean unreadOnly,
                                 Instant beforeAt, UUID beforeId, int limit);

    @Query(value = """
            SELECT count(*) FROM app_lm_notification
            WHERE recipient_user_id = :recipientUserId AND workspace_id = :workspaceId AND archived_at IS NULL
              AND read_at IS NULL AND (snoozed_until IS NULL OR snoozed_until <= :now)
            """ + ON_LIVE_POSITION, nativeQuery = true)
    long countUnread(UUID recipientUserId, UUID workspaceId, Instant now);

    @Query(value = """
            SELECT count(*) FROM app_lm_notification
            WHERE recipient_user_id = :recipientUserId AND workspace_id = :workspaceId AND kind = :kind
              AND archived_at IS NULL AND read_at IS NULL
            """ + ON_LIVE_POSITION, nativeQuery = true)
    long countUnreadOfKind(UUID recipientUserId, UUID workspaceId, String kind);

    /** The live row of a group, locked so two events folding into it queue. */
    @Query(value = """
            SELECT * FROM app_lm_notification
            WHERE recipient_user_id = :recipientUserId AND workspace_id = :workspaceId AND group_key = :groupKey
              AND read_at IS NULL AND archived_at IS NULL
            FOR UPDATE
            """, nativeQuery = true)
    Optional<Notification> lockLiveGroup(UUID recipientUserId, UUID workspaceId, String groupKey);

    @Modifying
    @Query(value = """
            UPDATE app_lm_notification SET read_at = :now, email_due_at = NULL, version = version + 1
            WHERE recipient_user_id = :recipientUserId AND workspace_id = :workspaceId AND read_at IS NULL
              AND archived_at IS NULL
            """ + ON_LIVE_POSITION, nativeQuery = true)
    int markAllRead(UUID recipientUserId, UUID workspaceId, Instant now);

    @Modifying
    @Query(value = """
            UPDATE app_lm_notification SET read_at = :now, email_due_at = NULL, version = version + 1
            WHERE recipient_user_id = :recipientUserId AND workspace_id = :workspaceId AND id IN (:ids)
              AND read_at IS NULL
            """, nativeQuery = true)
    int markRead(UUID recipientUserId, UUID workspaceId, List<UUID> ids, Instant now);

    /** People with an unread mention, per workspace — the digest's other half. */
    @Query(value = """
            SELECT DISTINCT workspace_id AS workspaceId, recipient_user_id AS userId FROM app_lm_notification
            WHERE kind = 'MENTIONED' AND read_at IS NULL AND archived_at IS NULL
            """ + ON_LIVE_POSITION, nativeQuery = true)
    List<NotificationRecipientRow> findUnreadMentionRecipients();
}
