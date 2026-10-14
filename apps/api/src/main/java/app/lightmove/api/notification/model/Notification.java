package app.lightmove.api.notification.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.notification.constant.NotificationKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row of a person's bell (V133). Rows sharing a group key merge while unread: a second event lands on the
 * live row — its count up, its latest people first — rather than beside it. It names a task or a note and never
 * quotes a note.
 */
@Entity
@Table(name = "app_lm_notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    private static final int NAMES_KEPT = 3;
    private static final String NAMES = "names";

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 24, updatable = false)
    private NotificationKind kind;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "person_id")
    private UUID personId;

    @Column(name = "candidate_id")
    private UUID candidateId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "note_id")
    private UUID noteId;

    @Column(name = "group_key", nullable = false, length = 200, updatable = false)
    private String groupKey;

    @Column(name = "event_count", nullable = false)
    private int eventCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> details = new HashMap<>();

    @Column(name = "latest_at", nullable = false)
    private Instant latestAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "snoozed_until")
    private Instant snoozedUntil;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "email_due_at")
    private Instant emailDueAt;

    @Column(name = "emailed_at")
    private Instant emailedAt;

    /** Folds one more event into this live row. */
    public void absorb(NotificationDraft draft, Instant at) {
        this.eventCount += Math.max(1, draft.count());
        this.actorUserId = draft.actorUserId();
        this.personId = draft.personId();
        this.candidateId = draft.candidateId();
        if (draft.taskId() != null) {
            this.taskId = draft.taskId();
        }
        if (draft.noteId() != null) {
            this.noteId = draft.noteId();
        }
        this.details = mergedDetails(details, draft);
        this.latestAt = at;
        this.snoozedUntil = null;
        if (emailDueAt == null && emailedAt == null && draft.emailDueAt() != null) {
            this.emailDueAt = draft.emailDueAt();
        }
    }

    /** Read before its email went, it never sends one: marking it unread later is a reminder, not news. */
    public void markRead(Instant at) {
        if (readAt == null) {
            this.readAt = at;
            this.emailDueAt = null;
        }
    }

    public void markUnread() {
        this.readAt = null;
    }

    public void snoozeUntil(Instant until) {
        this.snoozedUntil = until;
    }

    public void archive(Instant at) {
        this.archivedAt = at;
        markRead(at);
    }

    @SuppressWarnings("unchecked")
    public List<String> names() {
        Object names = details.get(NAMES);
        return names instanceof List<?> list ? (List<String>) list : List.of();
    }

    public static Map<String, Object> detailsOf(NotificationDraft draft) {
        return mergedDetails(Map.of(), draft);
    }

    private static Map<String, Object> mergedDetails(Map<String, Object> current, NotificationDraft draft) {
        Map<String, Object> merged = new HashMap<>(current);
        if (draft.name() != null) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            names.add(draft.name());
            Object held = current.get(NAMES);
            if (held instanceof List<?> list) {
                list.forEach(name -> names.add(String.valueOf(name)));
            }
            merged.put(NAMES, new ArrayList<>(names).subList(0, Math.min(NAMES_KEPT, names.size())));
        }
        if (draft.dueAt() != null) {
            merged.put("dueAt", draft.dueAt().toString());
        }
        return merged;
    }
}
