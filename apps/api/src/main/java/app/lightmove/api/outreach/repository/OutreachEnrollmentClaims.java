package app.lightmove.api.outreach.repository;

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

    private final JdbcTemplate jdbc;

    @Transactional
    public List<UUID> claimDue(Instant now, int limit) {
        Timestamp at = Timestamp.from(now);
        return jdbc.queryForList(CLAIM_DUE, UUID.class, at, at, limit);
    }
}
