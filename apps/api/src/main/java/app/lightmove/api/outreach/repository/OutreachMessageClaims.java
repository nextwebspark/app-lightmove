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
 * The dispatcher's claim on a consultant's scheduled emails, {@code OutreachEnrollmentClaims}' statement for the
 * message table: {@code SKIP LOCKED} passes over a line another instance is claiming, the committed
 * {@code sending_since} keeps every later dispatch away until it is released, and the version is bumped so an entity
 * loaded before the claim cannot write over it unnoticed.
 */
@Repository
@RequiredArgsConstructor
public class OutreachMessageClaims {

    private static final String CLAIM_DUE = """
            UPDATE app_lm_outreach_message
               SET sending_since = ?, version = version + 1
             WHERE id IN (SELECT id
                            FROM app_lm_outreach_message
                           WHERE status = 'SCHEDULED'
                             AND send_at <= ?
                             AND sending_since IS NULL
                           ORDER BY send_at
                           LIMIT ?
                             FOR UPDATE SKIP LOCKED)
            RETURNING id
            """;

    private static final String RECORD_DELIVERED = """
            UPDATE app_lm_outreach_message
               SET status = 'SENT', provider_message_id = ?, thread_id = coalesce(thread_id, ?), sent_at = ?,
                   occurred_at = ?, sending_since = NULL, version = version + 1
             WHERE id = ?
            """;

    private final JdbcTemplate jdbc;

    @Transactional
    public List<UUID> claimDue(Instant now, int limit) {
        Timestamp at = Timestamp.from(now);
        return jdbc.queryForList(CLAIM_DUE, UUID.class, at, at, limit);
    }

    /**
     * The last resort for an email that went but could not be recorded: it is marked sent with its ids, so it is
     * never sent again, it counts against the cap, and a reply is still matched to it.
     */
    @Transactional
    public void recordDelivered(UUID messageId, SentEmail sent, Instant now) {
        Timestamp at = Timestamp.from(now);
        jdbc.update(RECORD_DELIVERED, sent.messageId(), sent.threadId(), at, at, messageId);
    }
}
