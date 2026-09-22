package app.lightmove.api.core.ratelimit.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A per-workspace, per-day ceiling on a billed workload — the one rate limit in this application
 * that survives a second Cloud Run instance.
 *
 * <p>{@link LlmBudgetGuard} is the other kind and they are not substitutes. That one is per user,
 * per minute, in one JVM's heap: it makes a double-click cheap. This one is per firm, per day, in
 * the database: it is what stands between a workspace and a Vertex bill. A capped feature wants
 * both.
 *
 * <p>Counted in <b>calls, not tokens</b>. A coarse brake deliberately — the per-turn token columns
 * V65 put on {@code app_lm_assistant_turn} are where a real meter goes, and #430 owns building one.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceDailySpend {

    /**
     * Claim and count in one statement, so there is no read-then-write for two requests to race
     * through. A returned row means the call is allowed and says how many the firm has now spent;
     * <b>no row is the refusal</b>.
     *
     * <p>The {@code WHERE} guards only the {@code DO UPDATE} branch, so a ceiling of zero would let
     * the first call of the day insert regardless. That is why {@link #spend} refuses a ceiling
     * below one in Java rather than trusting this statement to express it.
     *
     * <p>The date is pinned to UTC here rather than taken from {@code current_date}: two instances
     * in two session timezones would otherwise disagree about when the day turned, and a firm would
     * get two days' budget out of the disagreement.
     */
    private static final String CLAIM = """
            INSERT INTO app_lm_workspace_daily_spend (workspace_id, meter, spend_date, calls)
            VALUES (:workspaceId, :meter, (now() AT TIME ZONE 'UTC')::date, 1)
            ON CONFLICT (workspace_id, meter, spend_date) DO UPDATE
                SET calls = app_lm_workspace_daily_spend.calls + 1, updated_at = now()
                WHERE app_lm_workspace_daily_spend.calls < :ceiling
            RETURNING calls
            """;

    private static final String SPENT = """
            SELECT calls FROM app_lm_workspace_daily_spend
            WHERE workspace_id = :workspaceId AND meter = :meter
              AND spend_date = (now() AT TIME ZONE 'UTC')::date
            """;

    private final JdbcClient jdbc;

    /**
     * Claims one call against today's budget.
     *
     * <p>{@code REQUIRES_NEW} so the count commits on its own: a caller that later fails, or that
     * runs in no transaction at all, must not be able to un-spend what it has already been billed
     * for. The deliberate consequence is that a provider outage after this point burns a call. A
     * compensating decrement is the obvious fix and is worse — a refund is a second write that can
     * itself fail or race, and double-spend or a negative counter is a worse failure than the one it
     * removes. Size the ceiling for it.
     *
     * @return how many calls the workspace has spent today, including this one, or empty when the
     *         ceiling is already reached and nothing was counted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Integer> spend(WorkspaceSpendMeter meter, UUID workspaceId, int ceiling) {
        if (ceiling < 1) {
            throw new IllegalArgumentException(
                    "A daily ceiling of " + ceiling + " would still admit the day's first call; "
                            + "disable the feature instead of capping it at zero");
        }
        return jdbc.sql(CLAIM)
                .param("workspaceId", workspaceId)
                .param("meter", meter.meter())
                .param("ceiling", ceiling)
                .query(Integer.class)
                .optional();
    }

    /** What the workspace has spent today, for an answer that wants to say how much is left. */
    @Transactional(readOnly = true)
    public int spentToday(WorkspaceSpendMeter meter, UUID workspaceId) {
        return jdbc.sql(SPENT)
                .param("workspaceId", workspaceId)
                .param("meter", meter.meter())
                .query(Integer.class)
                .optional()
                .orElse(0);
    }
}
