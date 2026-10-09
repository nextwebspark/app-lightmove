package app.lightmove.api.billing.credit.service;

import app.lightmove.api.billing.credit.model.MemberCreditSpend;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Captured finds per member over a period; a released or refunded hold spent nothing and is left out. */
@Component
@RequiredArgsConstructor
public class CreditSpendReport {

    private final JdbcTemplate jdbc;

    public List<MemberCreditSpend> byMember(UUID workspaceId, Instant from, Instant until) {
        return jdbc.query("""
                SELECT h.user_id, u.full_name,
                       count(*) FILTER (WHERE h.action = 'EMAIL_FOUND') AS emails,
                       count(*) FILTER (WHERE h.action = 'PHONE_FOUND') AS phones,
                       sum(h.credits) AS credits
                FROM app_lm_credit_hold h
                LEFT JOIN app_lm_user u ON u.id = h.user_id
                WHERE h.workspace_id = ? AND h.status = 'CAPTURED' AND h.created_at >= ? AND h.created_at < ?
                GROUP BY h.user_id, u.full_name
                ORDER BY credits DESC, u.full_name""",
                (row, i) -> new MemberCreditSpend(row.getObject("user_id", UUID.class), row.getString("full_name"),
                        row.getLong("emails"), row.getLong("phones"), row.getLong("credits")),
                workspaceId, Timestamp.from(from), Timestamp.from(until));
    }
}
