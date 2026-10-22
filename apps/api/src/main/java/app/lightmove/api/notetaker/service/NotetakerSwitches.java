package app.lightmove.api.notetaker.service;

import app.lightmove.api.outreach.model.KeptMeeting;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The three switches a call is joined under — the workspace's, the consultant's and the meeting's. Each is on
 * until somebody turns it off, so no row is on.
 */
@Component
@RequiredArgsConstructor
class NotetakerSwitches {

    private final JdbcTemplate jdbc;

    boolean workspaceAllows(UUID workspaceId) {
        return jdbc.query("SELECT enabled FROM app_lm_workspace_notetaker WHERE workspace_id = ?",
                        (row, index) -> row.getBoolean("enabled"), workspaceId)
                .stream().findFirst().orElse(true);
    }

    void setWorkspace(UUID workspaceId, boolean enabled, UUID actorId) {
        jdbc.update("""
                INSERT INTO app_lm_workspace_notetaker (workspace_id, enabled, updated_by)
                VALUES (?, ?, ?)
                ON CONFLICT (workspace_id)
                DO UPDATE SET enabled = excluded.enabled, updated_by = excluded.updated_by, updated_at = now()""",
                workspaceId, enabled, actorId);
    }

    boolean userAllows(UUID userId, UUID workspaceId) {
        return jdbc.query("SELECT enabled FROM app_lm_notetaker_user_preference WHERE user_id = ? AND workspace_id = ?",
                        (row, index) -> row.getBoolean("enabled"), userId, workspaceId)
                .stream().findFirst().orElse(true);
    }

    void setUser(UUID userId, UUID workspaceId, boolean enabled) {
        jdbc.update("""
                INSERT INTO app_lm_notetaker_user_preference (user_id, workspace_id, enabled)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id, workspace_id)
                DO UPDATE SET enabled = excluded.enabled, updated_at = now()""",
                userId, workspaceId, enabled);
    }

    /** The calendar events among these meetings somebody turned the Notetaker off for. */
    Set<String> optedOutAmong(Collection<KeptMeeting> meetings) {
        Set<String> optedOut = new HashSet<>();
        for (KeptMeeting meeting : meetings) {
            Boolean held = jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM app_lm_notetaker_meeting_opt_out
                                   WHERE mailbox_connection_id = ? AND provider_event_id = ?)""",
                    Boolean.class, meeting.mailboxConnectionId(), meeting.providerEventId());
            if (Boolean.TRUE.equals(held)) {
                optedOut.add(eventKeyOf(meeting));
            }
        }
        return optedOut;
    }

    boolean meetingAllows(Collection<KeptMeeting> sameCall) {
        return optedOutAmong(sameCall).isEmpty();
    }

    void setMeeting(Collection<KeptMeeting> sameCall, boolean enabled, UUID actorId) {
        for (KeptMeeting meeting : sameCall) {
            if (enabled) {
                jdbc.update("DELETE FROM app_lm_notetaker_meeting_opt_out "
                                + "WHERE mailbox_connection_id = ? AND provider_event_id = ?",
                        meeting.mailboxConnectionId(), meeting.providerEventId());
            } else {
                jdbc.update("""
                        INSERT INTO app_lm_notetaker_meeting_opt_out
                            (mailbox_connection_id, provider_event_id, workspace_id, opted_out_by)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (mailbox_connection_id, provider_event_id) DO NOTHING""",
                        meeting.mailboxConnectionId(), meeting.providerEventId(), meeting.workspaceId(), actorId);
            }
        }
    }

    static String eventKeyOf(KeptMeeting meeting) {
        return meeting.mailboxConnectionId() + "|" + meeting.providerEventId();
    }
}
