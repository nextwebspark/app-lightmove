package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.constant.MeetingMediaKind;
import app.lightmove.api.notetaker.model.AudioPiece;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The pieces a bot has uploaded (V152): one row each, written without touching the recording's own row. */
@Component
@RequiredArgsConstructor
class MeetingMediaPieces {

    private final JdbcTemplate jdbc;

    /** A piece sent again, after a retry the bot could not tell had landed, replaces the first. */
    void record(UUID recordingId, MeetingMediaKind kind, int sequence, String storageKey, long sizeBytes,
                Instant startedAt) {
        jdbc.update("""
                INSERT INTO app_lm_meeting_recording_media
                    (recording_id, kind, sequence, storage_key, size_bytes, started_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (recording_id, kind, sequence)
                DO UPDATE SET size_bytes = excluded.size_bytes, started_at = excluded.started_at, received_at = now()""",
                recordingId, kind.name(), sequence, storageKey, sizeBytes,
                startedAt == null ? null : Timestamp.from(startedAt));
    }

    List<String> storageKeysOf(UUID recordingId) {
        return jdbc.queryForList("SELECT storage_key FROM app_lm_meeting_recording_media WHERE recording_id = ? "
                + "ORDER BY kind, sequence", String.class, recordingId);
    }

    /**
     * Recordings whose pieces nobody will read that still hold some: a removed bot's at once, a finished one's
     * captions once its note is written, and a failed one's once it has been kept {@code failedBefore} to look into
     * (or to process again). A failed run is final until retried, so its {@code updated_at} is when it failed.
     */
    List<UUID> unwantedStillHoldingPieces(Instant failedBefore, int limit) {
        return jdbc.queryForList("""
                SELECT DISTINCT media.recording_id
                FROM app_lm_meeting_recording_media media
                JOIN app_lm_meeting_recording recording ON recording.id = media.recording_id
                WHERE recording.status IN ('REMOVED', 'DONE')
                   OR (recording.status = 'FAILED' AND recording.updated_at < ?)
                LIMIT ?""", UUID.class, Timestamp.from(failedBefore), limit);
    }

    /** Audio pieces no transcript piece covers yet, in the order they began. */
    List<AudioPiece> untranscribedAudioOf(UUID recordingId) {
        return jdbc.query("""
                SELECT media.sequence, media.storage_key, coalesce(media.started_at, media.received_at) AS started_at,
                       media.transcribe_attempts
                FROM app_lm_meeting_recording_media media
                WHERE media.recording_id = ? AND media.kind = 'AUDIO'
                  AND NOT EXISTS (SELECT 1 FROM app_lm_meeting_transcript_piece piece
                                  WHERE piece.recording_id = media.recording_id AND piece.sequence = media.sequence)
                ORDER BY 3, media.sequence""",
                (row, index) -> new AudioPiece(row.getInt("sequence"), row.getString("storage_key"),
                        row.getTimestamp("started_at").toInstant(), row.getInt("transcribe_attempts")),
                recordingId);
    }

    void countFailedAttempt(UUID recordingId, int sequence) {
        jdbc.update("UPDATE app_lm_meeting_recording_media SET transcribe_attempts = transcribe_attempts + 1 "
                + "WHERE recording_id = ? AND kind = 'AUDIO' AND sequence = ?", recordingId, sequence);
    }

    List<String> captionKeysOf(UUID recordingId) {
        return jdbc.queryForList("SELECT storage_key FROM app_lm_meeting_recording_media WHERE recording_id = ? "
                + "AND kind = 'CAPTIONS' ORDER BY sequence", String.class, recordingId);
    }

    /** When the last piece of anything arrived: how far a bot that stopped early got. */
    Optional<Instant> lastReceivedOf(UUID recordingId) {
        return Optional.ofNullable(jdbc.queryForObject("SELECT max(received_at) FROM app_lm_meeting_recording_media "
                + "WHERE recording_id = ?", Timestamp.class, recordingId)).map(Timestamp::toInstant);
    }

    /** Audio still to transcribe, or some already transcribed (its audio deleted once it was). */
    boolean holdsSpeech(UUID recordingId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM app_lm_meeting_recording_media WHERE recording_id = ? AND kind = 'AUDIO')
                    OR EXISTS (SELECT 1 FROM app_lm_meeting_transcript_piece WHERE recording_id = ?)""",
                Boolean.class, recordingId, recordingId));
    }

    /** Pieces received before {@code receivedBefore}, whatever became of their run: nothing captured outlives a day. */
    List<StoredPiece> receivedBefore(Instant receivedBefore, int limit) {
        return jdbc.query("""
                SELECT recording_id, storage_key FROM app_lm_meeting_recording_media
                WHERE received_at < ? ORDER BY received_at LIMIT ?""",
                (row, index) -> new StoredPiece(row.getObject("recording_id", UUID.class),
                        row.getString("storage_key")),
                Timestamp.from(receivedBefore), limit);
    }

    record StoredPiece(UUID recordingId, String storageKey) {}

    void forget(UUID recordingId, String storageKey) {
        jdbc.update("DELETE FROM app_lm_meeting_recording_media WHERE recording_id = ? AND storage_key = ?",
                recordingId, storageKey);
    }
}
