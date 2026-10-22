package app.lightmove.api.notetaker.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Which runs have transcription or a note to make, and the claim that keeps two instances off one run. The claim
 * commits on its own before any model is asked, and one held past {@code staleBefore} was left by an instance that
 * died, so it is taken over.
 */
@Component
@RequiredArgsConstructor
class NotetakerWorkClaims {

    private final JdbcTemplate jdbc;

    /**
     * A recording call holding audio that has waited {@code transcribeBefore} or longer, and every run waiting for
     * its note, that nobody is working.
     */
    List<UUID> due(Instant transcribeBefore, Instant staleBefore, int limit) {
        return jdbc.queryForList("""
                SELECT recording.id FROM app_lm_meeting_recording recording
                WHERE (recording.working_since IS NULL OR recording.working_since < ?)
                  AND (recording.status = 'PROCESSING'
                       OR (recording.status = 'RECORDING' AND EXISTS (
                           SELECT 1 FROM app_lm_meeting_recording_media media
                           WHERE media.recording_id = recording.id AND media.kind = 'AUDIO'
                             AND media.received_at < ?)))
                ORDER BY recording.updated_at
                LIMIT ?""", UUID.class, Timestamp.from(staleBefore), Timestamp.from(transcribeBefore), limit);
    }

    boolean claim(UUID recordingId, Instant now, Instant staleBefore) {
        return jdbc.update("""
                UPDATE app_lm_meeting_recording SET working_since = ?
                WHERE id = ? AND status IN ('RECORDING', 'PROCESSING')
                  AND (working_since IS NULL OR working_since < ?)""",
                Timestamp.from(now), recordingId, Timestamp.from(staleBefore)) == 1;
    }

    void release(UUID recordingId) {
        jdbc.update("UPDATE app_lm_meeting_recording SET working_since = NULL WHERE id = ?", recordingId);
    }
}
