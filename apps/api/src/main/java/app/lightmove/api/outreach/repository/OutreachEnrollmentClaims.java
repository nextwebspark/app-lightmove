package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.SentEmail;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dispatcher's claim on due enrollments, taken in one statement so several instances can run it at
 * once: {@code SKIP LOCKED} passes over a row another instance is claiming, and the committed
 * {@code sending_since} keeps every later dispatch away until the claim is released. The version is
 * bumped so an entity loaded before the claim cannot write over it unnoticed.
 */
@Repository
@RequiredArgsConstructor
public class OutreachEnrollmentClaims {

    private static final String CLAIM_DUE = """
            UPDATE app_lm_outreach_enrollment
               SET sending_since = ?, version = version + 1
             WHERE id IN (SELECT id
                            FROM app_lm_outreach_enrollment
                           WHERE status IN ('SCHEDULED', 'ACTIVE')
                             AND next_send_at <= ?
                             AND sending_since IS NULL
                           ORDER BY next_send_at
                           LIMIT ?
                             FOR UPDATE SKIP LOCKED)
            RETURNING id
            """;

    private static final String RECORD_DELIVERED = """
            UPDATE app_lm_outreach_enrollment
               SET thread_id = coalesce(thread_id, ?), last_message_id = ?, last_sent_at = ?,
                   next_step = next_step + 1, version = version + 1
             WHERE id = ?
            """;

    private final JdbcTemplate jdbc;

    @Transactional
    public List<UUID> claimDue(Instant now, int limit) {
        Timestamp at = Timestamp.from(now);
        return jdbc.queryForList(CLAIM_DUE, UUID.class, at, at, limit);
    }

    /**
     * The last resort for an email that went but could not be recorded: its thread and message ids, so a
     * reply is still matched to it. The claim stays, so the run ends as an uncertain send and nothing is resent.
     */
    @Transactional
    public void recordDelivered(UUID enrollmentId, SentEmail sent, Instant now) {
        jdbc.update(RECORD_DELIVERED, sent.threadId(), sent.messageId(), Timestamp.from(now), enrollmentId);
    }
}
