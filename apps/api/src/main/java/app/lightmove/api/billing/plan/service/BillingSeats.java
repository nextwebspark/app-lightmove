package app.lightmove.api.billing.plan.service;

import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.billing.usage.model.FairUsePeriod;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A workspace's staff seats and billing period as fair use counts them. A workspace founded after V118 has no
 * subscription yet, so its active staff are counted instead, as V118's backfill counted them; never fewer than one.
 */
@Service
@RequiredArgsConstructor
public class BillingSeats {

    private final WorkspaceSubscriptionRepository subscriptions;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public long staffSeatsOf(UUID workspaceId) {
        long seats = subscriptions.findByWorkspaceId(workspaceId)
                .map(WorkspaceSubscription::getSeats)
                .filter(subscribed -> subscribed > 0)
                .map(Integer::longValue)
                .orElseGet(() -> activeStaffOf(workspaceId));
        return Math.max(seats, 1);
    }

    /** The subscription's current period while {@code now} is inside it, else the UTC calendar month. */
    @Transactional(readOnly = true)
    public FairUsePeriod periodOf(UUID workspaceId, Instant now) {
        Optional<WorkspaceSubscription> subscription = subscriptions.findByWorkspaceId(workspaceId);
        if (subscription.isPresent()) {
            Instant start = subscription.get().getCurrentPeriodStart();
            Instant end = subscription.get().getCurrentPeriodEnd();
            if (start != null && end != null && !now.isBefore(start) && now.isBefore(end)) {
                return new FairUsePeriod(start, end);
            }
        }
        YearMonth month = YearMonth.from(now.atZone(ZoneOffset.UTC));
        return new FairUsePeriod(month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private long activeStaffOf(UUID workspaceId) {
        Long staff = jdbc.queryForObject("""
                SELECT count(DISTINCT m.id)
                FROM app_lm_workspace_member m
                JOIN app_lm_workspace_member_role mr ON mr.member_id = m.id
                JOIN app_lm_role r ON r.id = mr.role_id
                WHERE m.workspace_id = ? AND m.status = 'ACTIVE' AND r.name IN ('ADMIN', 'MEMBER')""",
                Long.class, workspaceId);
        return staff == null ? 0 : staff;
    }
}
