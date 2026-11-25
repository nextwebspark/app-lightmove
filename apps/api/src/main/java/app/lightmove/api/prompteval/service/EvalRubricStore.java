package app.lightmove.api.prompteval.service;

import app.lightmove.api.prompteval.constant.JudgePick;
import app.lightmove.api.prompteval.constant.JudgedWinner;
import app.lightmove.api.prompteval.model.EvalJudgement;
import app.lightmove.api.prompteval.model.EvalRubric;
import app.lightmove.api.prompteval.model.RubricCriterion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** The rubrics and the judge's picks on a run's pairs of answers (V240). Both are append-only. */
@Repository
@RequiredArgsConstructor
public class EvalRubricStore {

    private static final String RUBRICS =
            "SELECT id, dataset_id, version, criteria::text AS criteria FROM app_lm_eval_rubric ";

    private static final TypeReference<List<RubricCriterion>> CRITERIA = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate batches;
    private final ObjectMapper json;

    /** The dataset's rubric at its newest version. */
    Optional<EvalRubric> latestOf(UUID datasetId) {
        return jdbc.sql(RUBRICS + "WHERE dataset_id = :datasetId ORDER BY version DESC LIMIT 1")
                .param("datasetId", datasetId)
                .query(this::rubric)
                .optional();
    }

    Optional<EvalRubric> find(UUID rubricId) {
        return jdbc.sql(RUBRICS + "WHERE id = :id").param("id", rubricId).query(this::rubric).optional();
    }

    /** Every dataset's newest rubric, by dataset. */
    Map<UUID, EvalRubric> latestByDataset() {
        Map<UUID, EvalRubric> byDataset = new LinkedHashMap<>();
        jdbc.sql(RUBRICS + "ORDER BY dataset_id, version DESC").query(this::rubric).list()
                .forEach(rubric -> byDataset.putIfAbsent(rubric.datasetId(), rubric));
        return byDataset;
    }

    /** Records a release's rubric as the dataset's next version, unless its newest already says the same. */
    void ensure(UUID datasetId, List<RubricCriterion> criteria) {
        Optional<EvalRubric> latest = latestOf(datasetId);
        if (latest.isPresent() && latest.get().criteria().equals(criteria)) {
            return;
        }
        jdbc.sql("""
                        INSERT INTO app_lm_eval_rubric (dataset_id, version, criteria)
                        VALUES (:datasetId, :version, CAST(:criteria AS jsonb))
                        ON CONFLICT (dataset_id, version) DO NOTHING
                        """)
                .param("datasetId", datasetId)
                .param("version", latest.map(rubric -> rubric.version() + 1).orElse(1))
                .param("criteria", json.writeValueAsString(criteria))
                .update();
    }

    /** One pick per pair and criterion, once: a run picked up again that judges a pair it had judged adds nothing. */
    void addJudgements(UUID runId, List<EvalJudgement> judgements) {
        batches.batchUpdate("""
                INSERT INTO app_lm_eval_judgement
                    (run_id, case_id, repeat, criterion_key, candidate_shown_as, winner, reason)
                VALUES (:runId, :caseId, :repeat, :criterion, :shownAs, :winner, :reason)
                ON CONFLICT (run_id, case_id, repeat, criterion_key) DO NOTHING
                """, judgements.stream()
                .map(judgement -> new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("caseId", judgement.caseId())
                        .addValue("repeat", judgement.repeat())
                        .addValue("criterion", judgement.criterionKey())
                        .addValue("shownAs", judgement.candidateShownAs().name())
                        .addValue("winner", judgement.winner() == null ? null : judgement.winner().name())
                        .addValue("reason", judgement.reason()))
                .toArray(MapSqlParameterSource[]::new));
    }

    List<EvalJudgement> judgementsOf(UUID runId) {
        return jdbc.sql("""
                        SELECT case_id, repeat, criterion_key, candidate_shown_as, winner, reason
                        FROM app_lm_eval_judgement
                        WHERE run_id = :runId
                        ORDER BY case_id, repeat, id
                        """)
                .param("runId", runId)
                .query((row, index) -> {
                    String winner = row.getString("winner");
                    return new EvalJudgement(row.getObject("case_id", UUID.class), row.getInt("repeat"),
                            row.getString("criterion_key"), JudgePick.valueOf(row.getString("candidate_shown_as")),
                            winner == null ? null : JudgedWinner.valueOf(winner), row.getString("reason"));
                })
                .list();
    }

    private EvalRubric rubric(ResultSet row, int index) throws SQLException {
        return new EvalRubric(row.getObject("id", UUID.class), row.getObject("dataset_id", UUID.class),
                row.getInt("version"), json.readValue(row.getString("criteria"), CRITERIA));
    }
}
