package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.llm.constant.EvalCheckVerdict;
import app.lightmove.api.core.llm.model.PromptTrialResult;
import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.constant.EvalRunFailure;
import app.lightmove.api.prompteval.constant.EvalRunSide;
import app.lightmove.api.prompteval.constant.EvalRunStatus;
import app.lightmove.api.prompteval.constant.EvalRunVerdict;
import app.lightmove.api.prompteval.model.ClaimedEvalRun;
import app.lightmove.api.prompteval.model.EvalCallAverages;
import app.lightmove.api.prompteval.model.EvalRun;
import app.lightmove.api.prompteval.model.EvalRunAnswer;
import app.lightmove.api.prompteval.model.EvalRunCase;
import app.lightmove.api.prompteval.model.EvalRunPulse;
import app.lightmove.api.prompteval.model.EvalRunSummary;
import app.lightmove.api.prompteval.model.NewEvalRun;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The eval runs and what each version answered (V239). A worker's writes name its claim token, so one that lost
 * its run to another instance changes nothing.
 */
@Repository
@RequiredArgsConstructor
public class EvalRunStore {

    /** A run claimed this often and never ended is given up on rather than claimed for ever. */
    static final int MAX_CLAIMS = 3;

    private static final int MODEL_NAME_LENGTH = 80;

    private static final String RUNS = """
            SELECT r.id, r.number, r.prompt_id, r.dataset_id, d.name AS dataset_name, r.split, r.candidate_version,
                   r.baseline_version, r.candidate_model, r.baseline_model, r.repeats, r.status, r.verdict, r.failure,
                   r.gate_ready, r.case_count, r.calls_planned, r.calls_made, r.call_cap, r.input_tokens,
                   r.output_tokens, r.summary::text AS summary, r.rubric_id, r.judge_version, r.judge_model,
                   r.started_by, starter.full_name AS started_by_name, r.created_at, r.started_at, r.finished_at
            FROM app_lm_eval_run r
            JOIN app_lm_eval_dataset d ON d.id = r.dataset_id
            LEFT JOIN app_lm_user starter ON starter.id = r.started_by
            """;

    private static final TypeReference<Map<String, EvalCheckVerdict>> VERDICTS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    /**
     * Queues a run on the cases the dataset has in use in that split now. Empty when it has none. With a rubric,
     * the calls planned include the judge's: one for each pair of answers.
     *
     * @throws org.springframework.dao.DuplicateKeyException when a run is queued or going already
     */
    Optional<UUID> queue(NewEvalRun run) {
        return jdbc.sql("""
                        INSERT INTO app_lm_eval_run
                            (prompt_id, dataset_id, split, case_ids, candidate_version, baseline_version, repeats,
                             status, gate_ready, case_count, calls_planned, call_cap, started_by, rubric_id)
                        SELECT :promptId, :datasetId, :split, array_agg(c.id ORDER BY c.case_key), :candidate,
                               :baseline, :repeats, 'QUEUED', :gateReady, count(*),
                               count(*) * :repeats * CASE WHEN CAST(:rubricId AS uuid) IS NULL THEN 2 ELSE 3 END,
                               :callCap, :startedBy, CAST(:rubricId AS uuid)
                        FROM app_lm_eval_case c
                        WHERE c.dataset_id = :datasetId AND c.split = :split AND c.retired_at IS NULL
                        HAVING count(*) > 0
                        RETURNING id
                        """)
                .param("promptId", run.promptId())
                .param("datasetId", run.datasetId())
                .param("split", run.split().name())
                .param("candidate", run.candidateVersion())
                .param("baseline", run.baselineVersion())
                .param("repeats", run.repeats())
                .param("gateReady", run.gateReady())
                .param("callCap", run.callCap())
                .param("startedBy", run.startedBy())
                .param("rubricId", run.rubricId())
                .query(UUID.class)
                .optional();
    }

    Optional<EvalRun> find(UUID runId) {
        return jdbc.sql(RUNS + "WHERE r.id = :id").param("id", runId).query(this::run).optional();
    }

    List<EvalRun> latest(int limit) {
        return jdbc.sql(RUNS + "ORDER BY r.number DESC LIMIT :limit").param("limit", limit).query(this::run).list();
    }

    boolean isOneQueuedOrRunning() {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM app_lm_eval_run WHERE status IN ('QUEUED', 'RUNNING'))")
                .query(Boolean.class)
                .single();
    }

    /** A queued run, or a running one whose worker has gone quiet since {@code staleBefore}. */
    boolean isOneWaiting(Instant staleBefore) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM app_lm_eval_run
                                       WHERE status = 'QUEUED' OR (status = 'RUNNING' AND heartbeat_at < :staleBefore))
                        """)
                .param("staleBefore", Timestamp.from(staleBefore))
                .query(Boolean.class)
                .single();
    }

    /**
     * Takes the waiting run for this instance, if there is one: one statement, so two instances never both get it.
     * One claimed {@link #MAX_CLAIMS} times already is failed instead.
     */
    Optional<ClaimedEvalRun> claim(Instant staleBefore) {
        jdbc.sql("""
                        UPDATE app_lm_eval_run
                        SET status = 'FAILED', failure = 'STOPPED_UNEXPECTEDLY', finished_at = now()
                        WHERE status = 'RUNNING' AND heartbeat_at < :staleBefore AND claims >= :maxClaims
                        """)
                .param("staleBefore", Timestamp.from(staleBefore))
                .param("maxClaims", MAX_CLAIMS)
                .update();
        UUID token = UUID.randomUUID();
        return jdbc.sql("""
                        UPDATE app_lm_eval_run
                        SET status = 'RUNNING', claim_token = :token, claims = claims + 1, heartbeat_at = now(),
                            started_at = coalesce(started_at, now())
                        WHERE id = (SELECT id FROM app_lm_eval_run
                                    WHERE status = 'QUEUED' OR (status = 'RUNNING' AND heartbeat_at < :staleBefore)
                                    ORDER BY created_at
                                    LIMIT 1
                                    FOR UPDATE SKIP LOCKED)
                        RETURNING id
                        """)
                .param("token", token)
                .param("staleBefore", Timestamp.from(staleBefore))
                .query(UUID.class)
                .optional()
                .flatMap(this::find)
                .map(run -> new ClaimedEvalRun(run, token));
    }

    /** Shows the run is still being worked, and reads what the worker must know before its next call. */
    EvalRunPulse pulse(ClaimedEvalRun claimed) {
        return jdbc.sql("""
                        UPDATE app_lm_eval_run SET heartbeat_at = now()
                        WHERE id = :id AND status = 'RUNNING' AND claim_token = :token
                        RETURNING stop_requested_at IS NOT NULL AS stop_requested, calls_made
                        """)
                .param("id", claimed.run().id())
                .param("token", claimed.claimToken())
                .query((row, index) -> new EvalRunPulse(true, row.getBoolean("stop_requested"),
                        row.getInt("calls_made")))
                .optional()
                .orElse(EvalRunPulse.LOST);
    }

    /** The run's cases in key order, each with its input. */
    List<EvalRunCase> casesOf(UUID runId) {
        return jdbc.sql("""
                        SELECT c.id, c.case_key, c.name, c.tag, c.input::text AS input, c.expected::text AS expected
                        FROM app_lm_eval_run r
                        JOIN app_lm_eval_case c ON c.id = ANY (r.case_ids)
                        WHERE r.id = :id
                        ORDER BY c.case_key
                        """)
                .param("id", runId)
                .query((row, index) -> new EvalRunCase(row.getObject("id", UUID.class), row.getString("case_key"),
                        row.getString("name"), row.getString("tag"), json.readTree(row.getString("input")),
                        nodeOf(row.getString("expected"))))
                .list();
    }

    List<EvalRunAnswer> answersOf(UUID runId) {
        return jdbc.sql("""
                        SELECT case_id, side, repeat, answer::text AS answer, verdicts::text AS verdicts
                        FROM app_lm_eval_result
                        WHERE run_id = :id
                        ORDER BY case_id, side, repeat
                        """)
                .param("id", runId)
                .query((row, index) -> new EvalRunAnswer(row.getObject("case_id", UUID.class),
                        EvalRunSide.valueOf(row.getString("side")), row.getInt("repeat"),
                        nodeOf(row.getString("answer")), json.readValue(row.getString("verdicts"), VERDICTS)))
                .list();
    }

    /** One answer, once: a run picked up again that asks a case it had answered stores nothing new. */
    void addAnswer(UUID runId, EvalRunAnswer answer, PromptTrialResult asked) {
        jdbc.sql("""
                        INSERT INTO app_lm_eval_result
                            (run_id, case_id, side, repeat, answer, raw_answer, finish, input_tokens, output_tokens,
                             took_millis, verdicts)
                        VALUES (:runId, :caseId, :side, :repeat, CAST(:answer AS jsonb), :rawAnswer, :finish,
                                :inputTokens, :outputTokens, :tookMillis, CAST(:verdicts AS jsonb))
                        ON CONFLICT (run_id, case_id, side, repeat) DO NOTHING
                        """)
                .param("runId", runId)
                .param("caseId", answer.caseId())
                .param("side", answer.side().name())
                .param("repeat", answer.repeat())
                .param("answer", answer.answer() == null ? null : json.writeValueAsString(answer.answer()))
                .param("rawAnswer", asked.rawAnswer())
                .param("finish", asked.finish().name())
                .param("inputTokens", asked.inputTokens())
                .param("outputTokens", asked.outputTokens())
                .param("tookMillis", asked.tookMillis())
                .param("verdicts", json.writeValueAsString(answer.verdicts()))
                .update();
    }

    /** What an ask cost, answered or not, and the model that answered for that side. */
    void addSpend(UUID runId, EvalRunSide side, int calls, long inputTokens, long outputTokens, String model) {
        String modelColumn = side == EvalRunSide.CANDIDATE ? "candidate_model" : "baseline_model";
        jdbc.sql("""
                        UPDATE app_lm_eval_run
                        SET calls_made = calls_made + :calls, input_tokens = input_tokens + :inputTokens,
                            output_tokens = output_tokens + :outputTokens,
                            %1$s = coalesce(:model, %1$s)
                        WHERE id = :id
                        """.formatted(modelColumn))
                .param("id", runId)
                .param("calls", calls)
                .param("inputTokens", inputTokens)
                .param("outputTokens", outputTokens)
                .param("model", model == null || model.isBlank() ? null
                        : model.substring(0, Math.min(model.length(), MODEL_NAME_LENGTH)))
                .update();
    }

    /** What the judge's ask cost, answered or not, and the judge that was asked. */
    void addJudgeSpend(UUID runId, int judgeVersion, int calls, long inputTokens, long outputTokens, String model) {
        jdbc.sql("""
                        UPDATE app_lm_eval_run
                        SET calls_made = calls_made + :calls, input_tokens = input_tokens + :inputTokens,
                            output_tokens = output_tokens + :outputTokens, judge_version = :judgeVersion,
                            judge_model = coalesce(:model, judge_model)
                        WHERE id = :id
                        """)
                .param("id", runId)
                .param("judgeVersion", judgeVersion)
                .param("calls", calls)
                .param("inputTokens", inputTokens)
                .param("outputTokens", outputTokens)
                .param("model", model == null || model.isBlank() ? null
                        : model.substring(0, Math.min(model.length(), MODEL_NAME_LENGTH)))
                .update();
    }

    /** {@code verdict} null: the run finished and nothing it measured counts, so it gives no result. */
    boolean finish(ClaimedEvalRun claimed, EvalRunVerdict verdict, EvalRunSummary summary) {
        return jdbc.sql("""
                        UPDATE app_lm_eval_run
                        SET status = 'FINISHED', verdict = :verdict, summary = CAST(:summary AS jsonb),
                            finished_at = now()
                        WHERE id = :id AND status = 'RUNNING' AND claim_token = :token
                        """)
                .param("id", claimed.run().id())
                .param("token", claimed.claimToken())
                .param("verdict", verdict == null ? null : verdict.name())
                .param("summary", json.writeValueAsString(summary))
                .update() == 1;
    }

    boolean end(ClaimedEvalRun claimed, EvalRunStatus status, EvalRunFailure failure) {
        return jdbc.sql("""
                        UPDATE app_lm_eval_run SET status = :status, failure = :failure, finished_at = now()
                        WHERE id = :id AND status = 'RUNNING' AND claim_token = :token
                        """)
                .param("id", claimed.run().id())
                .param("token", claimed.claimToken())
                .param("status", status.name())
                .param("failure", failure == null ? null : failure.name())
                .update() == 1;
    }

    /**
     * Asks a run to stop: a queued one ends here, a running one before its next call. False when it had ended, or
     * a stop was asked for already.
     */
    boolean requestStop(UUID runId) {
        return jdbc.sql("""
                        UPDATE app_lm_eval_run
                        SET stop_requested_at = now(),
                            finished_at = CASE WHEN status = 'QUEUED' THEN now() ELSE finished_at END,
                            status = CASE WHEN status = 'QUEUED' THEN 'CANCELLED' ELSE status END
                        WHERE id = :id AND status IN ('QUEUED', 'RUNNING') AND stop_requested_at IS NULL
                        """)
                .param("id", runId)
                .update() == 1;
    }

    EvalCallAverages averagesOf(String promptId) {
        return jdbc.sql("""
                        SELECT avg(a.input_tokens + a.output_tokens)::float8 AS tokens,
                               avg(a.took_millis)::float8 AS millis
                        FROM app_lm_eval_result a
                        JOIN app_lm_eval_run r ON r.id = a.run_id
                        WHERE r.prompt_id = :promptId
                        """)
                .param("promptId", promptId)
                .query((row, index) -> new EvalCallAverages(row.getObject("tokens", Double.class),
                        row.getObject("millis", Double.class)))
                .single();
    }

    /** The mean length of a case's input in characters, for a prompt no run has measured yet. */
    double meanInputLengthOf(UUID datasetId, EvalCaseSplit split) {
        return jdbc.sql("""
                        SELECT coalesce(avg(length(input::text)), 0) FROM app_lm_eval_case
                        WHERE dataset_id = :datasetId AND split = :split AND retired_at IS NULL
                        """)
                .param("datasetId", datasetId)
                .param("split", split.name())
                .query(Double.class)
                .single();
    }

    /**
     * For each version of a prompt, its latest finished run on held-back cases against {@code baselineVersion} that
     * was started on a reviewed dataset: the only runs a put live rests on.
     */
    Map<Integer, EvalRun> latestCountingRuns(String promptId, int baselineVersion) {
        Map<Integer, EvalRun> byCandidate = new LinkedHashMap<>();
        jdbc.sql(RUNS + """
                        WHERE r.prompt_id = :promptId AND r.baseline_version = :baseline AND r.status = 'FINISHED'
                          AND r.split = 'HOLDOUT' AND r.gate_ready
                        ORDER BY r.finished_at DESC
                        """)
                .param("promptId", promptId)
                .param("baseline", baselineVersion)
                .query(this::run)
                .list()
                .forEach(run -> byCandidate.putIfAbsent(run.candidateVersion(), run));
        return byCandidate;
    }

    private JsonNode nodeOf(String stored) {
        return stored == null ? null : json.readTree(stored);
    }

    private EvalRun run(ResultSet row, int index) throws SQLException {
        String summary = row.getString("summary");
        String verdict = row.getString("verdict");
        String failure = row.getString("failure");
        return new EvalRun(
                row.getObject("id", UUID.class),
                row.getLong("number"),
                row.getString("prompt_id"),
                row.getObject("dataset_id", UUID.class),
                row.getString("dataset_name"),
                EvalCaseSplit.valueOf(row.getString("split")),
                row.getInt("candidate_version"),
                row.getInt("baseline_version"),
                row.getString("candidate_model"),
                row.getString("baseline_model"),
                row.getInt("repeats"),
                EvalRunStatus.valueOf(row.getString("status")),
                verdict == null ? null : EvalRunVerdict.valueOf(verdict),
                failure == null ? null : EvalRunFailure.valueOf(failure),
                row.getBoolean("gate_ready"),
                row.getInt("case_count"),
                row.getInt("calls_planned"),
                row.getInt("calls_made"),
                row.getInt("call_cap"),
                row.getLong("input_tokens"),
                row.getLong("output_tokens"),
                summary == null ? null : json.readValue(summary, EvalRunSummary.class),
                row.getObject("rubric_id", UUID.class),
                row.getObject("judge_version", Integer.class),
                row.getString("judge_model"),
                row.getObject("started_by", UUID.class),
                row.getString("started_by_name"),
                row.getTimestamp("created_at").toInstant(),
                instantOf(row.getTimestamp("started_at")),
                instantOf(row.getTimestamp("finished_at")));
    }

    private static Instant instantOf(Timestamp stored) {
        return stored == null ? null : stored.toInstant();
    }
}
