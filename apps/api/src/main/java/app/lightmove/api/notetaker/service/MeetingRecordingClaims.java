package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.model.ClaimedCall;
import app.lightmove.api.outreach.model.KeptMeeting;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Claims a call by inserting its recording: the unique call key lets exactly one instance's insert land, so two
 * schedulers passing over one meeting send one bot. Committed before any bot is sent.
 */
@Component
@RequiredArgsConstructor
class MeetingRecordingClaims {

    private final JdbcTemplate jdbc;

    Optional<UUID> claimLaunching(ClaimedCall call, String runTokenHash) {
        return insert(call, NotetakerStatus.LAUNCHING, null, runTokenHash);
    }

    /** A call the bot must not join is recorded once, so the reason shows and the next pass asks nothing more. */
    Optional<UUID> claimSkipped(ClaimedCall call, NotetakerEndReason reason) {
        return insert(call, NotetakerStatus.SKIPPED, reason, null);
    }

    private Optional<UUID> insert(ClaimedCall call, NotetakerStatus status, NotetakerEndReason reason,
                                  String runTokenHash) {
        KeptMeeting meeting = call.sentFrom();
        return jdbc.query("""
                        INSERT INTO app_lm_meeting_recording (workspace_id, call_key, user_id, person_id,
                                                              mailbox_connection_id, provider_event_id, platform,
                                                              join_url, title, starts_at, ends_at, status,
                                                              end_reason, run_token_hash)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (workspace_id, call_key) DO NOTHING
                        RETURNING id""",
                        (row, index) -> row.getObject("id", UUID.class),
                        meeting.workspaceId(), call.callKey(), meeting.userId(), meeting.personId(),
                        meeting.mailboxConnectionId(), meeting.providerEventId(), call.platform().name(),
                        meeting.joinUrl(), meeting.title(), Timestamp.from(meeting.startsAt()),
                        Timestamp.from(meeting.endsAt()), status.name(), reason == null ? null : reason.name(),
                        runTokenHash)
                .stream().findFirst();
    }
}
