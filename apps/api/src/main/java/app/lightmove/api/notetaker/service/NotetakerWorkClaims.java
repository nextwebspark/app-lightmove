package app.lightmove.api.notetaker.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The claim that keeps two instances off one run's transcription or note. It commits on its own before any model is
 * asked; its holder refreshes it as it goes, and one left unrefreshed past {@code staleBefore} belonged to an
 * instance that died, so it is taken over. A claim is known by its instant, kept to the database's microseconds.
 */
@Component
@RequiredArgsConstructor
class NotetakerWorkClaims {

    private final JdbcTemplate jdbc;

    /**
     * Claims the runs with work waiting — a call still going whose audio has waited {@code transcribeBefore}, and every
     * run waiting for its note — that nobody holds, so the scheduler hands each over once.
     */
    List<UUID> claimDue(Instant now, Instant transcribeBefore, Instant staleBefore, int limit) {
        return jdbc.queryForList("""
                UPDATE app_lm_meeting_recording SET working_since = ?
                WHERE id IN (
                    SELECT recording.id FROM app_lm_meeting_recording recording
                    WHERE (recording.working_since IS NULL OR recording.working_since < ?)
                      AND (recording.status = 'PROCESSING'
                           OR (recording.status = 'RECORDING' AND EXISTS (
                               SELECT 1 FROM app_lm_meeting_recording_media media
                               WHERE media.recording_id = recording.id AND media.kind = 'AUDIO'
                                 AND media.received_at < ?)))
                    ORDER BY recording.updated_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED)
                RETURNING id""", UUID.class, Timestamp.from(claimOf(now)), Timestamp.from(staleBefore),
                Timestamp.from(transcribeBefore), limit);
    }

    /** The claim's instant when this caller now holds the run. */
    Optional<Instant> claim(UUID recordingId, Instant now, Instant staleBefore) {
        Instant claim = claimOf(now);
        int claimed = jdbc.update("""
                UPDATE app_lm_meeting_recording SET working_since = ?
                WHERE id = ? AND status IN ('RECORDING', 'PROCESSING')
                  AND (working_since IS NULL OR working_since < ?)""",
                Timestamp.from(claim), recordingId, Timestamp.from(staleBefore));
        return claimed == 1 ? Optional.of(claim) : Optional.empty();
    }

    /** Moves the holder's claim on to {@code now}; empty when another instance has taken the run over. */
    Optional<Instant> refresh(UUID recordingId, Instant held, Instant now) {
        Instant claim = claimOf(now);
        int refreshed = jdbc.update("UPDATE app_lm_meeting_recording SET working_since = ? "
                + "WHERE id = ? AND working_since = ?", Timestamp.from(claim), recordingId, Timestamp.from(held));
        return refreshed == 1 ? Optional.of(claim) : Optional.empty();
    }

    /** Lets the run go, unless another instance has taken it over meanwhile. */
    void release(UUID recordingId, Instant held) {
        jdbc.update("UPDATE app_lm_meeting_recording SET working_since = NULL WHERE id = ? AND working_since = ?",
                recordingId, Timestamp.from(held));
    }

    static Instant claimOf(Instant now) {
        return now.truncatedTo(ChronoUnit.MICROS);
    }
}
