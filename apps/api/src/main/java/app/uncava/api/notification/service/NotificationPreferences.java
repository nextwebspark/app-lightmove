package app.uncava.api.notification.service;

import app.uncava.api.notification.constant.NotificationPreferenceKind;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** A person's choices per kind and channel in one workspace. No row means on, the digest included. */
@Component
@RequiredArgsConstructor
class NotificationPreferences {

    static final String DIGEST = "DIGEST";

    private final JdbcTemplate jdbc;

    Map<NotificationPreferenceKind, Channels> of(UUID userId, UUID workspaceId) {
        Map<NotificationPreferenceKind, Channels> chosen = new EnumMap<>(NotificationPreferenceKind.class);
        for (NotificationPreferenceKind kind : NotificationPreferenceKind.values()) {
            chosen.put(kind, new Channels(true, true));
        }
        jdbc.query("""
                SELECT kind, in_app, email FROM app_lm_notification_preference
                WHERE user_id = ? AND workspace_id = ? AND kind <> ?""", row -> {
            chosen.put(NotificationPreferenceKind.valueOf(row.getString("kind")),
                    new Channels(row.getBoolean("in_app"), row.getBoolean("email")));
        }, userId, workspaceId, DIGEST);
        return chosen;
    }

    Channels of(UUID userId, UUID workspaceId, NotificationPreferenceKind kind) {
        return of(userId, workspaceId).get(kind);
    }

    boolean digest(UUID userId, UUID workspaceId) {
        return jdbc.query("""
                SELECT email FROM app_lm_notification_preference
                WHERE user_id = ? AND workspace_id = ? AND kind = ?""",
                (row, index) -> row.getBoolean("email"), userId, workspaceId, DIGEST)
                .stream().findFirst().orElse(true);
    }

    void save(UUID userId, UUID workspaceId, String kind, boolean inApp, boolean email) {
        jdbc.update("""
                INSERT INTO app_lm_notification_preference (user_id, workspace_id, kind, in_app, email)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id, workspace_id, kind)
                DO UPDATE SET in_app = excluded.in_app, email = excluded.email, updated_at = now()""",
                userId, workspaceId, kind, inApp, email);
    }

    record Channels(boolean inApp, boolean email) {}
}
