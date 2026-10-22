package app.lightmove.api.notetaker.service;

import app.lightmove.api.outreach.model.KeptMeeting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The three switches a call is joined under — the workspace's, the consultant's and the meeting's. Each is on
 * until somebody turns it off, so no row is on. The set reads answer many rows in one query each: the scheduler
 * asks them of every workspace's calls every minute.
 */
@Component
@RequiredArgsConstructor
class NotetakerSwitches {

    private final JdbcTemplate jdbc;

    boolean workspaceAllows(UUID workspaceId) {
        return workspacesSwitchedOff(List.of(workspaceId)).isEmpty();
    }

    Set<UUID> workspacesSwitchedOff(Collection<UUID> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList("SELECT workspace_id FROM app_lm_workspace_notetaker "
                + "WHERE NOT enabled AND workspace_id IN (" + placeholders(workspaceIds.size()) + ")",
                UUID.class, workspaceIds.toArray()));
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

    /** The consultants among these calendars' owners who turned the Notetaker off, as {@link #userKeyOf} keys. */
    Set<String> usersSwitchedOff(Collection<KeptMeeting> meetings) {
        List<Object> pairs = new ArrayList<>();
        Set<String> asked = new HashSet<>();
        for (KeptMeeting meeting : meetings) {
            if (asked.add(userKeyOf(meeting.userId(), meeting.workspaceId()))) {
                pairs.add(meeting.userId());
                pairs.add(meeting.workspaceId());
            }
        }
        if (pairs.isEmpty()) {
            return Set.of();
        }
        Set<String> off = new HashSet<>();
        jdbc.query("SELECT user_id, workspace_id FROM app_lm_notetaker_user_preference WHERE NOT enabled "
                        + "AND (user_id, workspace_id) IN (" + pairPlaceholders(pairs.size() / 2) + ")",
                row -> {
                    off.add(userKeyOf(row.getObject("user_id", UUID.class), row.getObject("workspace_id", UUID.class)));
                }, pairs.toArray());
        return off;
    }

    void setUser(UUID userId, UUID workspaceId, boolean enabled) {
        jdbc.update("""
                INSERT INTO app_lm_notetaker_user_preference (user_id, workspace_id, enabled)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id, workspace_id)
                DO UPDATE SET enabled = excluded.enabled, updated_at = now()""",
                userId, workspaceId, enabled);
    }

    /** The calendar events among these meetings somebody turned the Notetaker off for, as {@link #eventKeyOf} keys. */
    Set<String> optedOutAmong(Collection<KeptMeeting> meetings) {
        List<Object> pairs = new ArrayList<>();
        for (KeptMeeting meeting : meetings) {
            pairs.add(meeting.mailboxConnectionId());
            pairs.add(meeting.providerEventId());
        }
        if (pairs.isEmpty()) {
            return Set.of();
        }
        Set<String> optedOut = new HashSet<>();
        jdbc.query("SELECT mailbox_connection_id, provider_event_id FROM app_lm_notetaker_meeting_opt_out "
                        + "WHERE (mailbox_connection_id, provider_event_id) IN (" + pairPlaceholders(pairs.size() / 2)
                        + ")",
                row -> {
                    optedOut.add(eventKeyOf(row.getObject("mailbox_connection_id", UUID.class),
                            row.getString("provider_event_id")));
                }, pairs.toArray());
        return optedOut;
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
        return eventKeyOf(meeting.mailboxConnectionId(), meeting.providerEventId());
    }

    static String userKeyOf(UUID userId, UUID workspaceId) {
        return userId + "|" + workspaceId;
    }

    private static String eventKeyOf(UUID mailboxConnectionId, String providerEventId) {
        return mailboxConnectionId + "|" + providerEventId;
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static String pairPlaceholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "(?, ?)"));
    }
}
