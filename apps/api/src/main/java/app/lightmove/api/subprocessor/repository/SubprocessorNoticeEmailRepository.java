package app.lightmove.api.subprocessor.repository;

import app.lightmove.api.subprocessor.model.OwedSubprocessorNoticeEmail;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Who a notice was emailed to in a workspace. A row is inserted before its email goes, carrying the time it is retried
 * if nothing records it sent: an instance that dies in between leaves it owed, never sent twice.
 */
@Repository
@RequiredArgsConstructor
public class SubprocessorNoticeEmailRepository {

    private static final String KEY = "notice_id = :noticeId AND workspace_id = :workspaceId AND user_id = :userId";

    private final JdbcClient jdbc;

    /** @return false when this admin was already owed or sent this notice: nothing more to send. */
    public boolean claim(UUID noticeId, UUID workspaceId, UUID userId, Instant retryAt) {
        return jdbc.sql("""
                        INSERT INTO app_lm_subprocessor_notice_email (notice_id, workspace_id, user_id, next_attempt_at)
                        VALUES (:noticeId, :workspaceId, :userId, :retryAt)
                        ON CONFLICT DO NOTHING
                        """)
                .param("noticeId", noticeId)
                .param("workspaceId", workspaceId)
                .param("userId", userId)
                .param("retryAt", Timestamp.from(retryAt))
                .update() == 1;
    }

    /** Takes an owed email for this attempt, unless another instance took it first. */
    public boolean reclaim(OwedSubprocessorNoticeEmail owed, Instant now, Instant retryAt) {
        return jdbc.sql("UPDATE app_lm_subprocessor_notice_email SET attempts = attempts + 1, next_attempt_at = :retryAt"
                        + " WHERE " + KEY + " AND sent_at IS NULL AND next_attempt_at <= :now")
                .param("retryAt", Timestamp.from(retryAt))
                .param("now", Timestamp.from(now))
                .param("noticeId", owed.noticeId())
                .param("workspaceId", owed.workspaceId())
                .param("userId", owed.userId())
                .update() == 1;
    }

    public void markSent(UUID noticeId, UUID workspaceId, UUID userId) {
        jdbc.sql("UPDATE app_lm_subprocessor_notice_email SET sent_at = now(), next_attempt_at = NULL WHERE " + KEY)
                .param("noticeId", noticeId)
                .param("workspaceId", workspaceId)
                .param("userId", userId)
                .update();
    }

    /** No longer owed: the admin left, or their workspace went. The row stays, the record that it was tried. */
    public void giveUp(OwedSubprocessorNoticeEmail owed) {
        jdbc.sql("UPDATE app_lm_subprocessor_notice_email SET next_attempt_at = NULL WHERE " + KEY
                        + " AND sent_at IS NULL")
                .param("noticeId", owed.noticeId())
                .param("workspaceId", owed.workspaceId())
                .param("userId", owed.userId())
                .update();
    }

    /** Due again at {@code at}, for a notice not yet in effect: the condition V248's resolver walks. */
    public List<OwedSubprocessorNoticeEmail> owedIn(UUID workspaceId, Instant at) {
        return jdbc.sql("""
                        SELECT e.notice_id, e.workspace_id, e.user_id, e.attempts
                          FROM app_lm_subprocessor_notice_email e
                          JOIN app_lm_subprocessor_notice n ON n.id = e.notice_id
                         WHERE e.workspace_id = :workspaceId AND e.sent_at IS NULL AND e.next_attempt_at <= :at
                           AND n.effective_on > :today
                         ORDER BY e.next_attempt_at
                        """)
                .param("workspaceId", workspaceId)
                .param("at", Timestamp.from(at))
                .param("today", LocalDate.ofInstant(at, ZoneOffset.UTC))
                .query((row, rowNumber) -> new OwedSubprocessorNoticeEmail(row.getObject("notice_id", UUID.class),
                        row.getObject("workspace_id", UUID.class), row.getObject("user_id", UUID.class),
                        row.getInt("attempts")))
                .list();
    }
}
