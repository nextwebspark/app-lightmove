package app.lightmove.api.prompteval.service;

import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.model.EvalCase;
import app.lightmove.api.prompteval.model.EvalDataset;
import app.lightmove.api.prompteval.model.NewEvalCase;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** The eval datasets and their cases (V238). */
@Repository
@RequiredArgsConstructor
public class EvalDatasetStore {

    private static final String SEED_LOCK = "eval-dataset-seed";

    private static final String DATASETS = """
            SELECT d.id, d.prompt_id, d.name, d.description,
                   count(c.id) FILTER (WHERE c.retired_at IS NULL) AS cases,
                   count(c.id) FILTER (WHERE c.retired_at IS NULL AND c.split = 'DEV') AS dev,
                   count(c.id) FILTER (WHERE c.retired_at IS NULL AND c.split = 'HOLDOUT') AS holdout,
                   greatest(d.created_at, max(c.created_at), max(c.retired_at)) AS updated_at
            FROM app_lm_eval_dataset d
            LEFT JOIN app_lm_eval_case c ON c.dataset_id = d.id
            """;

    private static final String CASES = """
            SELECT c.id, c.dataset_id, c.case_key, c.name, c.tag, c.split, c.created_at, c.retired_at,
                   author.full_name AS added_by
            FROM app_lm_eval_case c
            LEFT JOIN app_lm_user author ON author.id = c.created_by
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    /** Held to the end of the transaction, so instances booting together add a release's cases once. */
    void lockForSeed() {
        lock(SEED_LOCK);
    }

    /** Held to the end of the transaction, so two imports into one dataset add a case once. */
    void lockDataset(UUID datasetId) {
        lock("eval-dataset:" + datasetId);
    }

    UUID ensureDataset(String promptId, String name, String description) {
        return jdbc.sql("""
                        INSERT INTO app_lm_eval_dataset (prompt_id, name, description)
                        VALUES (:promptId, :name, :description)
                        ON CONFLICT (prompt_id, name) DO UPDATE SET description = EXCLUDED.description
                        RETURNING id
                        """)
                .param("promptId", promptId)
                .param("name", name)
                .param("description", description)
                .query(UUID.class)
                .single();
    }

    List<EvalDataset> datasets() {
        return jdbc.sql(DATASETS + "GROUP BY d.id ORDER BY d.name").query(EvalDatasetStore::dataset).list();
    }

    Optional<EvalDataset> dataset(UUID datasetId) {
        return jdbc.sql(DATASETS + "WHERE d.id = :id GROUP BY d.id")
                .param("id", datasetId)
                .query(EvalDatasetStore::dataset)
                .optional();
    }

    /** Cases in use first, in key order; the retired ones after them. */
    List<EvalCase> casesOf(UUID datasetId) {
        return jdbc.sql(CASES + "WHERE c.dataset_id = :datasetId ORDER BY (c.retired_at IS NOT NULL), c.case_key")
                .param("datasetId", datasetId)
                .query(EvalDatasetStore::evalCase)
                .list();
    }

    Optional<EvalCase> caseOf(UUID caseId) {
        return jdbc.sql(CASES + "WHERE c.id = :id").param("id", caseId).query(EvalDatasetStore::evalCase).optional();
    }

    /** Every key the dataset has ever held: a retired case's key is never given to another. */
    Set<String> keysOf(UUID datasetId) {
        return new HashSet<>(jdbc.sql("SELECT case_key FROM app_lm_eval_case WHERE dataset_id = :datasetId")
                .param("datasetId", datasetId)
                .query(String.class)
                .list());
    }

    /** Adds the cases whose key the dataset does not hold yet, and answers how many that was. */
    int add(UUID datasetId, List<NewEvalCase> cases, UUID createdBy) {
        int added = 0;
        for (NewEvalCase newCase : cases) {
            added += jdbc.sql("""
                            INSERT INTO app_lm_eval_case
                                (dataset_id, case_key, name, tag, split, input, expected, created_by)
                            VALUES (:datasetId, :key, :name, :tag, :split, CAST(:input AS jsonb),
                                    CAST(:expected AS jsonb), :createdBy)
                            ON CONFLICT (dataset_id, case_key) DO NOTHING
                            """)
                    .param("datasetId", datasetId)
                    .param("key", newCase.key())
                    .param("name", newCase.name())
                    .param("tag", newCase.tag())
                    .param("split", newCase.split().name())
                    .param("input", json.writeValueAsString(newCase.input()))
                    .param("expected", newCase.expected() == null || newCase.expected().isNull() ? null
                            : json.writeValueAsString(newCase.expected()))
                    .param("createdBy", createdBy)
                    .update();
        }
        return added;
    }

    /** False when the case was retired already. */
    boolean retire(UUID caseId, UUID retiredBy) {
        return jdbc.sql("""
                        UPDATE app_lm_eval_case SET retired_at = now(), retired_by = :retiredBy
                        WHERE id = :id AND retired_at IS NULL
                        """)
                .param("id", caseId)
                .param("retiredBy", retiredBy)
                .update() == 1;
    }

    private void lock(String key) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", key).query(row -> { });
    }

    private static EvalDataset dataset(ResultSet row, int index) throws SQLException {
        return new EvalDataset(
                row.getObject("id", UUID.class),
                row.getString("prompt_id"),
                row.getString("name"),
                row.getString("description"),
                row.getInt("cases"),
                row.getInt("dev"),
                row.getInt("holdout"),
                row.getTimestamp("updated_at").toInstant());
    }

    private static EvalCase evalCase(ResultSet row, int index) throws SQLException {
        Timestamp retiredAt = row.getTimestamp("retired_at");
        return new EvalCase(
                row.getObject("id", UUID.class),
                row.getObject("dataset_id", UUID.class),
                row.getString("case_key"),
                row.getString("name"),
                row.getString("tag"),
                EvalCaseSplit.valueOf(row.getString("split")),
                row.getString("added_by"),
                row.getTimestamp("created_at").toInstant(),
                retiredAt == null ? null : retiredAt.toInstant());
    }
}
