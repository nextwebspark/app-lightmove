package app.lightmove.api.workspace.service;

import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.workspace.model.WorkspacePurgeCertificate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The deletion certificate's row (V179). Begun with the counts of what the purge is about to delete, so a purge resumed
 * after a crash still certifies what was there, and completed with a body naming no one — ids, times and counts —
 * whose SHA-256 is kept beside it, so a copy someone was emailed can be checked against Uncava's.
 */
@Component
@RequiredArgsConstructor
public class WorkspacePurgeCertificates {

    private static final TypeReference<Map<String, Long>> COUNTS = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    /** Answers the certificate already begun, if a purge of this workspace started before. */
    public WorkspacePurgeCertificate begin(UUID workspaceId, Instant deletedAt, UUID deletedBy, Instant startedAt,
                                           Map<String, Long> rowCounts, Map<String, Long> retainedCounts) {
        jdbc.update("""
                INSERT INTO app_lm_workspace_purge_certificate
                    (workspace_id, deleted_at, deleted_by, purge_started_at, row_counts, retained_counts)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)
                ON CONFLICT (workspace_id) DO NOTHING""",
                workspaceId, Timestamp.from(deletedAt), deletedBy, Timestamp.from(startedAt),
                json.writeValueAsString(rowCounts), json.writeValueAsString(retainedCounts));
        return of(workspaceId).orElseThrow();
    }

    public void addDeletedFiles(UUID certificateId, long files) {
        jdbc.update("UPDATE app_lm_workspace_purge_certificate SET object_count = object_count + ? WHERE id = ?",
                files, certificateId);
    }

    /** Writes the body once; a certificate already completed keeps the body it was hashed and sent with. */
    public WorkspacePurgeCertificate complete(UUID workspaceId, Instant purgedAt) {
        WorkspacePurgeCertificate begun = of(workspaceId).orElseThrow();
        if (begun.isComplete()) {
            return begun;
        }
        String body = bodyOf(begun, purgedAt);
        jdbc.update("""
                UPDATE app_lm_workspace_purge_certificate SET purged_at = ?, body = ?, body_sha256 = ?
                WHERE id = ? AND purged_at IS NULL""",
                Timestamp.from(purgedAt), body, Tokens.hash(body), begun.id());
        return of(workspaceId).orElseThrow();
    }

    public Optional<WorkspacePurgeCertificate> of(UUID workspaceId) {
        List<WorkspacePurgeCertificate> found = jdbc.query("""
                SELECT id, workspace_id, deleted_at, deleted_by, purge_started_at, row_counts, retained_counts,
                       object_count, purged_at, body, body_sha256
                FROM app_lm_workspace_purge_certificate WHERE workspace_id = ?""",
                (row, index) -> certificateOf(row), workspaceId);
        return found.stream().findFirst();
    }

    /** Sorted keys and fixed field order: the same purge always reads as the same bytes, so its hash checks. */
    private String bodyOf(WorkspacePurgeCertificate begun, Instant purgedAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("certificate", begun.id().toString());
        body.put("workspace", begun.workspaceId().toString());
        body.put("deletedAt", begun.deletedAt().toString());
        body.put("purgeStartedAt", begun.purgeStartedAt().toString());
        body.put("purgedAt", purgedAt.toString());
        body.put("deletedRows", new TreeMap<>(begun.rowCounts()));
        body.put("deletedFiles", begun.objectCount());
        body.put("retainedRows", new TreeMap<>(begun.retainedCounts()));
        body.put("retainedBecause", "The audit trail and billing records are kept by id: they name no one, and the law "
                + "requires financial records to outlive a customer.");
        return json.writeValueAsString(body);
    }

    private WorkspacePurgeCertificate certificateOf(ResultSet row) throws SQLException {
        Timestamp purgedAt = row.getTimestamp("purged_at");
        return new WorkspacePurgeCertificate(row.getObject("id", UUID.class), row.getObject("workspace_id", UUID.class),
                row.getTimestamp("deleted_at").toInstant(), row.getObject("deleted_by", UUID.class),
                row.getTimestamp("purge_started_at").toInstant(),
                json.readValue(row.getString("row_counts"), COUNTS),
                json.readValue(row.getString("retained_counts"), COUNTS),
                row.getLong("object_count"), purgedAt == null ? null : purgedAt.toInstant(),
                row.getString("body"), row.getString("body_sha256"));
    }
}
