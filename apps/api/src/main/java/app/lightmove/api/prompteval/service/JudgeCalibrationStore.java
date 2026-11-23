package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.llm.model.PromptTrialResult;
import app.lightmove.api.prompteval.constant.CalibrationCheckStatus;
import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.constant.JudgePick;
import app.lightmove.api.prompteval.model.CalibrationCheck;
import app.lightmove.api.prompteval.model.CalibrationLabel;
import app.lightmove.api.prompteval.model.CalibrationLabelling;
import app.lightmove.api.prompteval.model.CalibrationPair;
import app.lightmove.api.prompteval.model.CalibrationPick;
import app.lightmove.api.prompteval.model.ClaimedCalibrationCheck;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The pairs a person labels for a rubric, their labels, and each check of the judge against them (V240). A
 * worker's writes name its claim token, so one that lost its check to another instance changes nothing.
 */
@Repository
@RequiredArgsConstructor
public class JudgeCalibrationStore {

    /** A check claimed this often and never ended is given up on rather than claimed for ever. */
    static final int MAX_CLAIMS = 3;

    private static final int MODEL_NAME_LENGTH = 80;

    /** Two versions' answers to the same dev case and repeat in a finished run, where both answered. */
    private static final String DRAWABLE = """
            FROM app_lm_eval_run r
            JOIN app_lm_eval_result c ON c.run_id = r.id AND c.side = 'CANDIDATE' AND c.answer IS NOT NULL
            JOIN app_lm_eval_result b ON b.run_id = r.id AND b.side = 'BASELINE' AND b.case_id = c.case_id
                                     AND b.repeat = c.repeat AND b.answer IS NOT NULL
            WHERE r.dataset_id = :datasetId AND r.split = 'DEV' AND r.status = 'FINISHED'
            """;

    private static final String CHECKS = """
            SELECT id, rubric_id, judge_version, judge_model, status, agreed, picks, started_by, created_at,
                   finished_at
            FROM app_lm_eval_calibration_check
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    /** Held to the end of the transaction, so two people opening Label pairs together draw one set. */
    void lockRubric(UUID rubricId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "eval-rubric:" + rubricId)
                .query(row -> { });
    }

    int drawablePairs(UUID datasetId) {
        return jdbc.sql("SELECT count(*) " + DRAWABLE).param("datasetId", datasetId).query(Integer.class).single();
    }

    /**
     * Draws up to {@code pairs} pairs at random, a case's second pair only once every case has given its first,
     * and which answer is A at random too. Answers how many were drawn.
     */
    int draw(UUID rubricId, UUID datasetId, int pairs) {
        return jdbc.sql("""
                        INSERT INTO app_lm_eval_calibration_pair (rubric_id, position, case_id, a_result_id, b_result_id)
                        SELECT :rubricId, row_number() OVER (ORDER BY per_case, shuffle), case_id,
                               CASE WHEN flipped THEN baseline_id ELSE candidate_id END,
                               CASE WHEN flipped THEN candidate_id ELSE baseline_id END
                        FROM (SELECT c.case_id, c.id AS candidate_id, b.id AS baseline_id,
                                     random() < 0.5 AS flipped, random() AS shuffle,
                                     row_number() OVER (PARTITION BY c.case_id
                                                        ORDER BY r.finished_at DESC, random()) AS per_case
                              %s) drawable
                        ORDER BY per_case, shuffle
                        LIMIT :pairs
                        """.formatted(DRAWABLE))
                .param("rubricId", rubricId)
                .param("datasetId", datasetId)
                .param("pairs", pairs)
                .update();
    }

    List<CalibrationPair> pairsOf(UUID rubricId) {
        return jdbc.sql("""
                        SELECT p.id, p.position, p.case_id, c.case_key, c.name, c.split, c.input::text AS input,
                               c.expected::text AS expected, a.answer::text AS answer_a, b.answer::text AS answer_b
                        FROM app_lm_eval_calibration_pair p
                        JOIN app_lm_eval_case c ON c.id = p.case_id
                        JOIN app_lm_eval_result a ON a.id = p.a_result_id
                        JOIN app_lm_eval_result b ON b.id = p.b_result_id
                        WHERE p.rubric_id = :rubricId
                        ORDER BY p.position
                        """)
                .param("rubricId", rubricId)
                .query((row, index) -> new CalibrationPair(row.getObject("id", UUID.class), row.getInt("position"),
                        row.getObject("case_id", UUID.class), row.getString("case_key"), row.getString("name"),
                        EvalCaseSplit.valueOf(row.getString("split")), nodeOf(row.getString("input")),
                        nodeOf(row.getString("expected")), nodeOf(row.getString("answer_a")),
                        nodeOf(row.getString("answer_b"))))
                .list();
    }

    Optional<UUID> rubricOfPair(UUID pairId) {
        return jdbc.sql("SELECT rubric_id FROM app_lm_eval_calibration_pair WHERE id = :id")
                .param("id", pairId)
                .query(UUID.class)
                .optional();
    }

    List<CalibrationLabel> labelsOf(UUID rubricId) {
        return jdbc.sql("""
                        SELECT l.pair_id, l.criterion_key, l.pick
                        FROM app_lm_eval_calibration_label l
                        JOIN app_lm_eval_calibration_pair p ON p.id = l.pair_id
                        WHERE p.rubric_id = :rubricId
                        """)
                .param("rubricId", rubricId)
                .query((row, index) -> new CalibrationLabel(row.getObject("pair_id", UUID.class),
                        row.getString("criterion_key"), JudgePick.valueOf(row.getString("pick"))))
                .list();
    }

    /** A pick saved again unchanged is left as it was, so reviewing labels does not read as changing them. */
    void label(UUID pairId, String criterionKey, JudgePick pick, UUID labelledBy) {
        jdbc.sql("""
                        INSERT INTO app_lm_eval_calibration_label AS label (pair_id, criterion_key, pick, labelled_by)
                        VALUES (:pairId, :criterion, :pick, :labelledBy)
                        ON CONFLICT (pair_id, criterion_key) DO UPDATE
                            SET pick = EXCLUDED.pick, labelled_by = EXCLUDED.labelled_by, labelled_at = now()
                            WHERE label.pick <> EXCLUDED.pick
                        """)
                .param("pairId", pairId)
                .param("criterion", criterionKey)
                .param("pick", pick.name())
                .param("labelledBy", labelledBy)
                .update();
    }

    CalibrationLabelling labellingOf(UUID rubricId, int criteria) {
        return jdbc.sql("""
                        SELECT (SELECT count(*) FROM app_lm_eval_calibration_pair WHERE rubric_id = :rubricId) AS pairs,
                               (SELECT count(*) FROM (SELECT l.pair_id
                                                      FROM app_lm_eval_calibration_label l
                                                      JOIN app_lm_eval_calibration_pair p ON p.id = l.pair_id
                                                      WHERE p.rubric_id = :rubricId
                                                      GROUP BY l.pair_id
                                                      HAVING count(*) >= :criteria) whole) AS labelled,
                               last.full_name, last.labelled_at
                        FROM (SELECT 1) one
                        LEFT JOIN LATERAL (SELECT u.full_name, l.labelled_at
                                           FROM app_lm_eval_calibration_label l
                                           JOIN app_lm_eval_calibration_pair p ON p.id = l.pair_id
                                           LEFT JOIN app_lm_user u ON u.id = l.labelled_by
                                           WHERE p.rubric_id = :rubricId
                                           ORDER BY l.labelled_at DESC
                                           LIMIT 1) last ON true
                        """)
                .param("rubricId", rubricId)
                .param("criteria", criteria)
                .query((row, index) -> new CalibrationLabelling(row.getInt("pairs"), row.getInt("labelled"),
                        row.getString("full_name"), instantOf(row.getTimestamp("labelled_at"))))
                .single();
    }

    boolean isLabelChangedSince(UUID rubricId, Instant since) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM app_lm_eval_calibration_label l
                                       JOIN app_lm_eval_calibration_pair p ON p.id = l.pair_id
                                       WHERE p.rubric_id = :rubricId AND l.labelled_at > :since)
                        """)
                .param("rubricId", rubricId)
                .param("since", Timestamp.from(since))
                .query(Boolean.class)
                .single();
    }

    Optional<CalibrationCheck> latestCheck(UUID rubricId) {
        return jdbc.sql(CHECKS + "WHERE rubric_id = :rubricId ORDER BY created_at DESC LIMIT 1")
                .param("rubricId", rubricId)
                .query(JudgeCalibrationStore::check)
                .optional();
    }

    Optional<CalibrationCheck> latestFinishedCheck(UUID rubricId) {
        return jdbc.sql(CHECKS + "WHERE rubric_id = :rubricId AND status = 'FINISHED' ORDER BY created_at DESC LIMIT 1")
                .param("rubricId", rubricId)
                .query(JudgeCalibrationStore::check)
                .optional();
    }

    Optional<CalibrationCheck> findCheck(UUID checkId) {
        return jdbc.sql(CHECKS + "WHERE id = :id").param("id", checkId).query(JudgeCalibrationStore::check).optional();
    }

    /** @throws org.springframework.dao.DuplicateKeyException when a check of this rubric is queued or going */
    UUID queueCheck(UUID rubricId, int judgeVersion, UUID startedBy) {
        return jdbc.sql("""
                        INSERT INTO app_lm_eval_calibration_check (rubric_id, judge_version, status, started_by)
                        VALUES (:rubricId, :judgeVersion, 'QUEUED', :startedBy)
                        RETURNING id
                        """)
                .param("rubricId", rubricId)
                .param("judgeVersion", judgeVersion)
                .param("startedBy", startedBy)
                .query(UUID.class)
                .single();
    }

    boolean isOneWaiting(Instant staleBefore) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM app_lm_eval_calibration_check
                                       WHERE status = 'QUEUED' OR (status = 'RUNNING' AND heartbeat_at < :staleBefore))
                        """)
                .param("staleBefore", Timestamp.from(staleBefore))
                .query(Boolean.class)
                .single();
    }

    /** Takes a waiting check for this instance: one statement, so two instances never both get it. */
    Optional<ClaimedCalibrationCheck> claim(Instant staleBefore) {
        jdbc.sql("""
                        UPDATE app_lm_eval_calibration_check SET status = 'FAILED', finished_at = now()
                        WHERE status = 'RUNNING' AND heartbeat_at < :staleBefore AND claims >= :maxClaims
                        """)
                .param("staleBefore", Timestamp.from(staleBefore))
                .param("maxClaims", MAX_CLAIMS)
                .update();
        UUID token = UUID.randomUUID();
        return jdbc.sql("""
                        UPDATE app_lm_eval_calibration_check
                        SET status = 'RUNNING', claim_token = :token, claims = claims + 1, heartbeat_at = now()
                        WHERE id = (SELECT id FROM app_lm_eval_calibration_check
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
                .flatMap(this::findCheck)
                .map(check -> new ClaimedCalibrationCheck(check, token));
    }

    /** Shows the check is still being worked. False: it ended or another instance took it over. */
    boolean pulse(ClaimedCalibrationCheck claimed) {
        return jdbc.sql("""
                        UPDATE app_lm_eval_calibration_check SET heartbeat_at = now()
                        WHERE id = :id AND status = 'RUNNING' AND claim_token = :token
                        """)
                .param("id", claimed.check().id())
                .param("token", claimed.claimToken())
                .update() == 1;
    }

    void addPicks(UUID checkId, boolean aShownFirst, List<CalibrationPick> picks) {
        for (CalibrationPick pick : picks) {
            jdbc.sql("""
                            INSERT INTO app_lm_eval_calibration_pick
                                (check_id, pair_id, criterion_key, a_shown_first, pick, reason)
                            VALUES (:checkId, :pairId, :criterion, :aShownFirst, :pick, :reason)
                            ON CONFLICT (check_id, pair_id, criterion_key) DO NOTHING
                            """)
                    .param("checkId", checkId)
                    .param("pairId", pick.pairId())
                    .param("criterion", pick.criterionKey())
                    .param("aShownFirst", aShownFirst)
                    .param("pick", pick.pick() == null ? null : pick.pick().name())
                    .param("reason", pick.reason())
                    .update();
        }
    }

    List<CalibrationPick> picksOf(UUID checkId) {
        return jdbc.sql("""
                        SELECT pair_id, criterion_key, pick, reason FROM app_lm_eval_calibration_pick
                        WHERE check_id = :checkId
                        """)
                .param("checkId", checkId)
                .query((row, index) -> {
                    String pick = row.getString("pick");
                    return new CalibrationPick(row.getObject("pair_id", UUID.class), row.getString("criterion_key"),
                            pick == null ? null : JudgePick.valueOf(pick), row.getString("reason"));
                })
                .list();
    }

    int pairsJudged(UUID checkId) {
        return jdbc.sql("SELECT count(DISTINCT pair_id) FROM app_lm_eval_calibration_pick WHERE check_id = :checkId")
                .param("checkId", checkId)
                .query(Integer.class)
                .single();
    }

    void addSpend(UUID checkId, PromptTrialResult asked) {
        String model = asked.model();
        jdbc.sql("""
                        UPDATE app_lm_eval_calibration_check
                        SET calls_made = calls_made + :calls, input_tokens = input_tokens + :inputTokens,
                            output_tokens = output_tokens + :outputTokens, judge_model = coalesce(:model, judge_model)
                        WHERE id = :id
                        """)
                .param("id", checkId)
                .param("calls", Math.max(1, asked.modelCalls()))
                .param("inputTokens", asked.inputTokens())
                .param("outputTokens", asked.outputTokens())
                .param("model", model == null || model.isBlank() ? null
                        : model.substring(0, Math.min(model.length(), MODEL_NAME_LENGTH)))
                .update();
    }

    /**
     * Ends the check with the judge's agreement over every label: a pick the judge left unreadable is one it did
     * not agree on.
     */
    boolean finish(ClaimedCalibrationCheck claimed) {
        return jdbc.sql("""
                        UPDATE app_lm_eval_calibration_check AS checked
                        SET status = 'FINISHED', finished_at = now(), agreed = compared.agreed, picks = compared.picks
                        FROM (SELECT count(*) AS picks, count(*) FILTER (WHERE k.pick = l.pick) AS agreed
                              FROM app_lm_eval_calibration_label l
                              JOIN app_lm_eval_calibration_pair p ON p.id = l.pair_id
                              LEFT JOIN app_lm_eval_calibration_pick k
                                     ON k.check_id = :id AND k.pair_id = l.pair_id
                                    AND k.criterion_key = l.criterion_key
                              WHERE p.rubric_id = :rubricId) compared
                        WHERE checked.id = :id AND checked.status = 'RUNNING' AND checked.claim_token = :token
                        """)
                .param("id", claimed.check().id())
                .param("rubricId", claimed.check().rubricId())
                .param("token", claimed.claimToken())
                .update() == 1;
    }

    boolean fail(ClaimedCalibrationCheck claimed) {
        return jdbc.sql("""
                        UPDATE app_lm_eval_calibration_check SET status = 'FAILED', finished_at = now()
                        WHERE id = :id AND status = 'RUNNING' AND claim_token = :token
                        """)
                .param("id", claimed.check().id())
                .param("token", claimed.claimToken())
                .update() == 1;
    }

    private JsonNode nodeOf(String stored) {
        return stored == null ? null : json.readTree(stored);
    }

    private static CalibrationCheck check(ResultSet row, int index) throws SQLException {
        return new CalibrationCheck(
                row.getObject("id", UUID.class),
                row.getObject("rubric_id", UUID.class),
                row.getInt("judge_version"),
                row.getString("judge_model"),
                CalibrationCheckStatus.valueOf(row.getString("status")),
                row.getObject("agreed", Integer.class),
                row.getObject("picks", Integer.class),
                row.getObject("started_by", UUID.class),
                row.getTimestamp("created_at").toInstant(),
                instantOf(row.getTimestamp("finished_at")));
    }

    private static Instant instantOf(Timestamp stored) {
        return stored == null ? null : stored.toInstant();
    }
}
