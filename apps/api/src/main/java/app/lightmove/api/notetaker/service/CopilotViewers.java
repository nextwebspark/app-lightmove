package app.lightmove.api.notetaker.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Who has a copilot window open on a live call (V153): each window's stream stamps its holder as it connects, which it
 * does again at least every 55 seconds, so a stamp a little older than that means the window closed.
 */
@Component
@RequiredArgsConstructor
class CopilotViewers {

    static final Duration STILL_WATCHING = Duration.ofSeconds(75);

    private final JdbcTemplate jdbc;

    void seen(UUID recordingId, UUID userId, Instant at) {
        jdbc.update("""
                INSERT INTO app_lm_meeting_copilot_viewer (recording_id, user_id, seen_at) VALUES (?, ?, ?)
                ON CONFLICT (recording_id, user_id) DO UPDATE SET seen_at = EXCLUDED.seen_at
                """, recordingId, userId, Timestamp.from(at));
    }

    List<UUID> watching(UUID recordingId, Instant now) {
        return jdbc.queryForList("""
                SELECT user_id FROM app_lm_meeting_copilot_viewer
                WHERE recording_id = ? AND seen_at > ? ORDER BY seen_at
                """, UUID.class, recordingId, Timestamp.from(now.minus(STILL_WATCHING)));
    }
}
