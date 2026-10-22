package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.constant.MeetingMediaKind;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The pieces a bot has uploaded (V151): one row each, written without touching the recording's own row. */
@Component
@RequiredArgsConstructor
class MeetingMediaPieces {

    private final JdbcTemplate jdbc;

    /** A piece sent again, after a retry the bot could not tell had landed, replaces the first. */
    void record(UUID recordingId, MeetingMediaKind kind, int sequence, String storageKey, long sizeBytes) {
        jdbc.update("""
                INSERT INTO app_lm_meeting_recording_media (recording_id, kind, sequence, storage_key, size_bytes)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (recording_id, kind, sequence)
                DO UPDATE SET size_bytes = excluded.size_bytes, received_at = now()""",
                recordingId, kind.name(), sequence, storageKey, sizeBytes);
    }

    List<String> storageKeysOf(UUID recordingId) {
        return jdbc.queryForList("SELECT storage_key FROM app_lm_meeting_recording_media WHERE recording_id = ? "
                + "ORDER BY kind, sequence", String.class, recordingId);
    }

    /** Recordings a consultant removed the bot from that still hold pieces. */
    List<UUID> removedStillHoldingPieces(int limit) {
        return jdbc.queryForList("""
                SELECT DISTINCT media.recording_id
                FROM app_lm_meeting_recording_media media
                JOIN app_lm_meeting_recording recording ON recording.id = media.recording_id
                WHERE recording.status = 'REMOVED'
                LIMIT ?""", UUID.class, limit);
    }

    void forget(UUID recordingId, String storageKey) {
        jdbc.update("DELETE FROM app_lm_meeting_recording_media WHERE recording_id = ? AND storage_key = ?",
                recordingId, storageKey);
    }
}
