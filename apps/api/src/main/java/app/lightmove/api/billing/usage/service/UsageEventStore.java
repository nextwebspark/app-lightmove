package app.lightmove.api.billing.usage.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.model.MonthlyUsage;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code app_lm_usage_event}, by SQL: a keyed use is inserted once, whoever races to it. An insert is its own
 * transaction, since a worker may record from an {@code AFTER_COMMIT} listener whose committed connection is still
 * bound to the thread.
 */
@Component
@RequiredArgsConstructor
class UsageEventStore {

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean insert(MeteredUse use, long estCostFils) {
        return jdbc.update("""
                INSERT INTO app_lm_usage_event
                    (workspace_id, user_id, project_id, kind, units, est_cost_fils, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (workspace_id, idempotency_key) DO NOTHING""",
                use.workspaceId(), use.userId(), use.projectId(), use.kind().name(), use.units(), estCostFils,
                use.idempotencyKey()) == 1;
    }

    boolean contains(UUID workspaceId, String idempotencyKey) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM app_lm_usage_event WHERE workspace_id = ? AND idempotency_key = ?)""",
                Boolean.class, workspaceId, idempotencyKey));
    }

    long unitsBetween(UUID workspaceId, UsageKind kind, Instant from, Instant to) {
        Long units = jdbc.queryForObject("""
                SELECT coalesce(sum(units), 0) FROM app_lm_usage_event
                WHERE workspace_id = ? AND kind = ? AND created_at >= ? AND created_at < ?""",
                Long.class, workspaceId, kind.name(), Timestamp.from(from), Timestamp.from(to));
        return units == null ? 0 : units;
    }

    List<MonthlyUsage> monthlyBetween(YearMonth first, YearMonth last) {
        Instant from = first.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = last.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return jdbc.query("""
                SELECT workspace_id, to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM') AS month, kind,
                       sum(units) AS units, sum(est_cost_fils) AS est_cost_fils
                FROM app_lm_usage_event
                WHERE created_at >= ? AND created_at < ?
                GROUP BY workspace_id, month, kind
                ORDER BY workspace_id, month, kind""",
                (row, index) -> new MonthlyUsage(row.getObject("workspace_id", UUID.class),
                        YearMonth.parse(row.getString("month")), UsageKind.valueOf(row.getString("kind")),
                        row.getLong("units"), row.getLong("est_cost_fils")),
                Timestamp.from(from), Timestamp.from(to));
    }
}
