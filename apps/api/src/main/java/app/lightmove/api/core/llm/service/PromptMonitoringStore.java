package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.PromptOutcome;
import app.lightmove.api.core.llm.model.LiveVersionComparison;
import app.lightmove.api.core.llm.model.PromptModelUsage;
import app.lightmove.api.core.llm.model.PromptVersionOutcomes;
import app.lightmove.api.core.llm.model.PromptVersionRef;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads of the daily call counts (V235, V243), and the daily count of what people did with an answer (V243). */
@Repository
@RequiredArgsConstructor
public class PromptMonitoringStore {

    private final JdbcClient jdbc;

    /**
     * Counted only against a version that has served: one a client echoes back is not taken on trust, and a version
     * never put live drafted nothing a person could have decided on.
     */
    void add(PromptVersionRef answeredBy, PromptOutcome outcome) {
        jdbc.sql("""
                        INSERT INTO app_lm_platform_prompt_outcome_daily AS daily
                            (prompt_id, version, day, accepted, changed)
                        SELECT v.prompt_id, v.version, (now() AT TIME ZONE 'UTC')::date, :accepted, :changed
                        FROM app_lm_platform_prompt_version v
                        WHERE v.prompt_id = :promptId AND v.version = :version
                          AND EXISTS (
                              SELECT 1 FROM app_lm_platform_prompt_label_move m
                              WHERE m.prompt_id = v.prompt_id AND m.to_version = v.version AND m.label = :label
                          )
                        ON CONFLICT (prompt_id, version, day) DO UPDATE SET
                            accepted = daily.accepted + EXCLUDED.accepted,
                            changed = daily.changed + EXCLUDED.changed
                        """)
                .param("promptId", answeredBy.promptId())
                .param("version", answeredBy.version())
                .param("label", PromptRegistryStore.PRODUCTION)
                .param("accepted", outcome == PromptOutcome.ACCEPTED ? 1 : 0)
                .param("changed", outcome == PromptOutcome.CHANGED ? 1 : 0)
                .update();
    }

    /** Each version and model of a prompt over the last {@code days} UTC days, today included. */
    public List<PromptModelUsage> usageOf(String promptId, int days) {
        return jdbc.sql("""
                        SELECT version, provider, model_id,
                               sum(calls) AS calls, sum(input_tokens) AS input_tokens,
                               sum(output_tokens) AS output_tokens, sum(finish_stop) AS finish_stop,
                               sum(finish_max_tokens) AS finish_max_tokens, sum(finish_safety) AS finish_safety,
                               sum(finish_other) AS finish_other, sum(guard_refusals) AS guard_refusals,
                               sum(schema_reasks) AS schema_reasks, sum(schema_failures) AS schema_failures
                        FROM app_lm_platform_prompt_call_daily
                        WHERE prompt_id = :promptId AND day > (now() AT TIME ZONE 'UTC')::date - :days
                        GROUP BY version, provider, model_id
                        ORDER BY version DESC, sum(calls) DESC, model_id
                        """)
                .param("promptId", promptId)
                .param("days", days)
                .query((row, index) -> new PromptModelUsage(row.getInt("version"),
                        AiProvider.valueOf(row.getString("provider")), row.getString("model_id"),
                        row.getLong("calls"), row.getLong("input_tokens"), row.getLong("output_tokens"),
                        row.getLong("finish_stop"), row.getLong("finish_max_tokens"), row.getLong("finish_safety"),
                        row.getLong("finish_other"), row.getLong("guard_refusals"), row.getLong("schema_reasks"),
                        row.getLong("schema_failures")))
                .list();
    }

    public List<PromptVersionOutcomes> outcomesOf(String promptId, int days) {
        return jdbc.sql("""
                        SELECT version, sum(accepted) AS accepted, sum(changed) AS changed
                        FROM app_lm_platform_prompt_outcome_daily
                        WHERE prompt_id = :promptId AND day > (now() AT TIME ZONE 'UTC')::date - :days
                        GROUP BY version
                        """)
                .param("promptId", promptId)
                .param("days", days)
                .query((row, index) -> new PromptVersionOutcomes(row.getInt("version"), row.getLong("accepted"),
                        row.getLong("changed")))
                .list();
    }

    /**
     * Every prompt whose live version replaced another: the live one over its first days live, the replaced one
     * over as many days before that, {@code maxDays} at most. The day of the move counts for the live version only.
     */
    public List<LiveVersionComparison> liveVersionComparisons(int maxDays) {
        return liveVersionComparisons(null, maxDays);
    }

    /** The same for one prompt; empty when its live version replaced none. */
    public Optional<LiveVersionComparison> liveVersionComparisonOf(String promptId, int maxDays) {
        return liveVersionComparisons(promptId, maxDays).stream().findFirst();
    }

    /**
     * A failure is a call, never two: an answer cut short is counted under its finish reason only, and
     * {@code schema_failures} holds only the answers that finished and still would not bind.
     */
    private List<LiveVersionComparison> liveVersionComparisons(String onlyPromptId, int maxDays) {
        return jdbc.sql("""
                        WITH last_move AS (
                            SELECT DISTINCT ON (m.prompt_id) m.prompt_id, m.from_version, m.to_version,
                                   (m.moved_at AT TIME ZONE 'UTC')::date AS moved_on
                            FROM app_lm_platform_prompt_label_move m
                            WHERE m.label = :label AND (:onlyPromptId IS NULL OR m.prompt_id = :onlyPromptId)
                            ORDER BY m.prompt_id, m.id DESC
                        ), compared AS (
                            SELECT prompt_id, from_version, to_version, moved_on,
                                   LEAST(:maxDays, (now() AT TIME ZONE 'UTC')::date - moved_on + 1) AS days,
                                   (now() AT TIME ZONE 'UTC')::date - moved_on + 1 AS days_live
                            FROM last_move
                            WHERE from_version IS NOT NULL AND from_version <> to_version
                        )
                        SELECT c.prompt_id, c.from_version, c.moved_on, c.days, c.days_live,
                               live.calls AS live_calls, live.failures AS live_failures,
                               previous.calls AS previous_calls, previous.failures AS previous_failures,
                               live_outcome.accepted AS live_accepted, live_outcome.changed AS live_changed,
                               previous_outcome.accepted AS previous_accepted,
                               previous_outcome.changed AS previous_changed
                        FROM compared c
                        CROSS JOIN LATERAL (
                            SELECT coalesce(sum(d.calls), 0) AS calls,
                                   coalesce(sum(d.finish_max_tokens + d.finish_safety + d.schema_failures), 0)
                                       AS failures
                            FROM app_lm_platform_prompt_call_daily d
                            WHERE d.prompt_id = c.prompt_id AND d.version = c.to_version
                              AND d.day >= c.moved_on AND d.day < c.moved_on + c.days
                        ) live
                        CROSS JOIN LATERAL (
                            SELECT coalesce(sum(d.calls), 0) AS calls,
                                   coalesce(sum(d.finish_max_tokens + d.finish_safety + d.schema_failures), 0)
                                       AS failures
                            FROM app_lm_platform_prompt_call_daily d
                            WHERE d.prompt_id = c.prompt_id AND d.version = c.from_version
                              AND d.day >= c.moved_on - c.days AND d.day < c.moved_on
                        ) previous
                        CROSS JOIN LATERAL (
                            SELECT coalesce(sum(o.accepted), 0) AS accepted, coalesce(sum(o.changed), 0) AS changed
                            FROM app_lm_platform_prompt_outcome_daily o
                            WHERE o.prompt_id = c.prompt_id AND o.version = c.to_version
                              AND o.day >= c.moved_on AND o.day < c.moved_on + c.days
                        ) live_outcome
                        CROSS JOIN LATERAL (
                            SELECT coalesce(sum(o.accepted), 0) AS accepted, coalesce(sum(o.changed), 0) AS changed
                            FROM app_lm_platform_prompt_outcome_daily o
                            WHERE o.prompt_id = c.prompt_id AND o.version = c.from_version
                              AND o.day >= c.moved_on - c.days AND o.day < c.moved_on
                        ) previous_outcome
                        """)
                .param("label", PromptRegistryStore.PRODUCTION)
                .param("onlyPromptId", onlyPromptId, Types.VARCHAR)
                .param("maxDays", maxDays)
                .query((row, index) -> new LiveVersionComparison(row.getString("prompt_id"),
                        row.getInt("from_version"), row.getObject("moved_on", LocalDate.class), row.getInt("days"),
                        row.getInt("days_live"), row.getLong("live_calls"), row.getLong("live_failures"),
                        row.getLong("previous_calls"), row.getLong("previous_failures"),
                        row.getLong("live_accepted"), row.getLong("live_changed"),
                        row.getLong("previous_accepted"), row.getLong("previous_changed")))
                .list();
    }
}
