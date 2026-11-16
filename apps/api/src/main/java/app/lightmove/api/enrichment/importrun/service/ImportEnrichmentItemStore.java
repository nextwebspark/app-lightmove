package app.lightmove.api.enrichment.importrun.service;

import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentKind;
import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentOutcome;
import app.lightmove.api.enrichment.importrun.model.ClaimedEnrichmentItem;
import app.lightmove.api.enrichment.importrun.model.ImportEnrichmentProgress;
import app.lightmove.api.enrichment.importrun.model.ImportEnrichmentTarget;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * V225's rows, one statement each and no transaction held: the worker calls a vendor between a claim and its finish.
 * Every statement names the workspace beside the row-level policy, which answers it too.
 */
@Component
@RequiredArgsConstructor
class ImportEnrichmentItemStore {

    /** A row claimed this often and never finished is given up on rather than claimed forever. */
    static final int MAX_ATTEMPTS = 3;

    private static final RowMapper<ClaimedEnrichmentItem> CLAIMED = (rs, rowNum) -> new ClaimedEnrichmentItem(
            rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
            rs.getObject("project_id", UUID.class), rs.getObject("run_id", UUID.class),
            rs.getObject("requested_by", UUID.class), ImportEnrichmentKind.valueOf(rs.getString("kind")),
            rs.getObject("target_id", UUID.class), rs.getString("linkedin_slug"));

    private final JdbcTemplate jdbc;

    void queue(UUID workspaceId, UUID projectId, UUID runId, UUID requestedBy,
               Collection<ImportEnrichmentTarget> targets) {
        jdbc.batchUpdate("""
                        INSERT INTO app_lm_import_enrichment_item
                            (workspace_id, project_id, run_id, requested_by, kind, target_id, linkedin_slug)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (run_id, kind, target_id) DO NOTHING
                        """,
                targets, targets.size(), (statement, target) -> {
                    statement.setObject(1, workspaceId);
                    statement.setObject(2, projectId);
                    statement.setObject(3, runId);
                    statement.setObject(4, requestedBy);
                    statement.setString(5, target.kind().name());
                    statement.setObject(6, target.targetId());
                    statement.setString(7, target.linkedinSlug());
                });
    }

    /**
     * One row more, added while the run is under way — a company first learned from its person's research — and only
     * while the run holds fewer than {@code runCeiling} rows, so a follow-on cannot carry a run past the import's cap.
     */
    boolean queueOne(ClaimedEnrichmentItem from, ImportEnrichmentTarget target, int runCeiling) {
        return jdbc.update("""
                        INSERT INTO app_lm_import_enrichment_item
                            (workspace_id, project_id, run_id, requested_by, kind, target_id, linkedin_slug)
                        SELECT ?, ?, ?, ?, ?, ?, ?
                        WHERE (SELECT count(*) FROM app_lm_import_enrichment_item
                               WHERE workspace_id = ? AND run_id = ?) < ?
                        ON CONFLICT (run_id, kind, target_id) DO NOTHING
                        """, from.workspaceId(), from.projectId(), from.runId(), from.requestedBy(),
                target.kind().name(), target.targetId(), target.linkedinSlug(),
                from.workspaceId(), from.runId(), runCeiling) > 0;
    }

    /** Oldest first, skipping rows another instance holds; a claim its instance never finished is taken again. */
    List<ClaimedEnrichmentItem> claim(UUID workspaceId, Instant staleBefore, int limit) {
        return jdbc.query("""
                UPDATE app_lm_import_enrichment_item
                SET status = 'RUNNING', claimed_at = now(), attempts = attempts + 1
                WHERE id IN (SELECT id FROM app_lm_import_enrichment_item
                             WHERE workspace_id = ? AND attempts < ?
                               AND (status = 'QUEUED' OR (status = 'RUNNING' AND claimed_at < ?))
                             ORDER BY created_at
                             LIMIT ?
                             FOR UPDATE SKIP LOCKED)
                RETURNING id, workspace_id, project_id, run_id, requested_by, kind, target_id, linkedin_slug
                """, CLAIMED, workspaceId, MAX_ATTEMPTS, Timestamp.from(staleBefore), limit);
    }

    int giveUpStale(UUID workspaceId, Instant staleBefore) {
        return jdbc.update("""
                UPDATE app_lm_import_enrichment_item SET status = 'FAILED', finished_at = now()
                WHERE workspace_id = ? AND status = 'RUNNING' AND claimed_at < ? AND attempts >= ?
                """, workspaceId, Timestamp.from(staleBefore), MAX_ATTEMPTS);
    }

    void finish(UUID workspaceId, UUID itemId, ImportEnrichmentOutcome outcome) {
        jdbc.update("""
                UPDATE app_lm_import_enrichment_item SET status = ?, finished_at = now()
                WHERE workspace_id = ? AND id = ?
                """, outcome.name(), workspaceId, itemId);
    }

    ImportEnrichmentProgress progressOf(UUID workspaceId, UUID projectId, UUID runId) {
        Map<ImportEnrichmentOutcome, Integer> counts = new EnumMap<>(ImportEnrichmentOutcome.class);
        jdbc.query("""
                SELECT status, count(*) AS row_count FROM app_lm_import_enrichment_item
                WHERE workspace_id = ? AND project_id = ? AND run_id = ?
                GROUP BY status
                """, rs -> {
            counts.put(ImportEnrichmentOutcome.valueOf(rs.getString("status")), rs.getInt("row_count"));
        }, workspaceId, projectId, runId);
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return new ImportEnrichmentProgress(total,
                counts.getOrDefault(ImportEnrichmentOutcome.QUEUED, 0)
                        + counts.getOrDefault(ImportEnrichmentOutcome.RUNNING, 0),
                counts.getOrDefault(ImportEnrichmentOutcome.RESEARCHED, 0),
                counts.getOrDefault(ImportEnrichmentOutcome.ALREADY_RESEARCHED, 0),
                counts.getOrDefault(ImportEnrichmentOutcome.NOT_FOUND, 0),
                counts.getOrDefault(ImportEnrichmentOutcome.REFUSED, 0),
                counts.getOrDefault(ImportEnrichmentOutcome.OVER_ALLOWANCE, 0),
                counts.getOrDefault(ImportEnrichmentOutcome.UNAVAILABLE, 0),
                counts.getOrDefault(ImportEnrichmentOutcome.FAILED, 0));
    }

    boolean runExists(UUID workspaceId, UUID projectId, UUID runId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM app_lm_import_enrichment_item
                               WHERE workspace_id = ? AND project_id = ? AND run_id = ?)
                """, Boolean.class, workspaceId, projectId, runId);
        return Boolean.TRUE.equals(exists);
    }

    int purgeFinishedBefore(UUID workspaceId, Instant before) {
        return jdbc.update("""
                DELETE FROM app_lm_import_enrichment_item WHERE workspace_id = ? AND finished_at < ?
                """, workspaceId, Timestamp.from(before));
    }

    /** An erased person's rows, by slug and by the mandates' rows that mapped them, in one statement. */
    void erasePerson(UUID workspaceId, String profileSlug, Collection<UUID> candidateIds) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM app_lm_import_enrichment_item
                    WHERE workspace_id = ? AND kind = 'PERSON' AND (linkedin_slug = ? OR target_id = ANY (?))
                    """);
            statement.setObject(1, workspaceId);
            statement.setString(2, profileSlug);
            statement.setArray(3, connection.createArrayOf("uuid", candidateIds.toArray()));
            return statement;
        });
    }
}
