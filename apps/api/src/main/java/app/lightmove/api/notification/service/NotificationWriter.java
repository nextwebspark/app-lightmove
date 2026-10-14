package app.lightmove.api.notification.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.stream.UserStreamPublisher;
import app.lightmove.api.notification.model.Notification;
import app.lightmove.api.notification.model.NotificationDraft;
import app.lightmove.api.notification.repository.NotificationRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Puts one event in a person's bell: a new row, or folded into the live row of its group. A row turned off in
 * their settings is not written; an assignment or a mention they want by email is given its email time. The
 * person's open tabs are nudged at commit.
 */
@Component
@RequiredArgsConstructor
class NotificationWriter {

    private static final String INSERT_FIRST_OF_GROUP = """
            INSERT INTO app_lm_notification (workspace_id, recipient_user_id, kind, actor_user_id, project_id,
                                             person_id, candidate_id, task_id, note_id, group_key, event_count,
                                             details, latest_at, email_due_at)
            VALUES (?, ?, ?, CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid),
                    CAST(? AS uuid), ?, ?, CAST(? AS jsonb), ?, CAST(? AS timestamptz))
            ON CONFLICT (recipient_user_id, workspace_id, group_key) WHERE read_at IS NULL AND archived_at IS NULL
            DO NOTHING
            """;

    private final JdbcTemplate jdbc;
    private final NotificationRepository rows;
    private final NotificationPreferences preferences;
    private final UserStreamPublisher stream;
    private final ObjectMapper json;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(NotificationDraft draft) {
        NotificationPreferences.Channels channels = preferences.of(draft.recipientUserId(), draft.workspaceId(),
                draft.kind().preference());
        if (!channels.inApp()) {
            return;
        }
        Instant now = clock.instant();
        NotificationDraft placed = draft.kind().emailable() && channels.email()
                ? draft.withEmailDueAt(now.plus(properties.notifications().emailDelay()))
                : draft;
        int written = jdbc.update(INSERT_FIRST_OF_GROUP, placed.workspaceId(), placed.recipientUserId(),
                placed.kind().name(), text(placed.actorUserId()), text(placed.projectId()), text(placed.personId()),
                text(placed.candidateId()), text(placed.taskId()), text(placed.noteId()), placed.groupKey(),
                Math.max(1, placed.count()), json.writeValueAsString(Notification.detailsOf(placed)),
                Timestamp.from(now), placed.emailDueAt() == null ? null : Timestamp.from(placed.emailDueAt()));
        if (written == 0) {
            rows.lockLiveGroup(placed.recipientUserId(), placed.workspaceId(), placed.groupKey())
                    .ifPresent(live -> live.absorb(placed, now));
        }
        stream.publish(placed.recipientUserId(), placed.workspaceId());
    }

    private static String text(Object id) {
        return id == null ? null : id.toString();
    }
}
