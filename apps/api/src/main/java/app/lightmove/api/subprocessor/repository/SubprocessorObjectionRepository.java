package app.lightmove.api.subprocessor.repository;

import app.lightmove.api.subprocessor.model.SubprocessorObjection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** A workspace's objections, under row-level security: every read names the workspace it is in as well. */
@Repository
@RequiredArgsConstructor
public class SubprocessorObjectionRepository {

    private static final String COLUMNS = """
            SELECT o.notice_id, o.workspace_id, o.reason, o.objected_at, author.full_name AS objected_by_name
              FROM app_lm_subprocessor_objection o
              LEFT JOIN app_lm_user author ON author.id = o.objected_by_user_id
            """;

    private final JdbcClient jdbc;

    public List<SubprocessorObjection> ofWorkspace(UUID workspaceId) {
        return jdbc.sql(COLUMNS + " WHERE o.workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .query(SubprocessorObjectionRepository::objectionOf)
                .list();
    }

    public Optional<SubprocessorObjection> find(UUID workspaceId, UUID noticeId) {
        return jdbc.sql(COLUMNS + " WHERE o.workspace_id = :workspaceId AND o.notice_id = :noticeId")
                .param("workspaceId", workspaceId)
                .param("noticeId", noticeId)
                .query(SubprocessorObjectionRepository::objectionOf)
                .optional();
    }

    /** A second objection from the workspace replaces its first, with who wrote it and when. */
    public void save(UUID workspaceId, UUID noticeId, UUID userId, String reason) {
        jdbc.sql("""
                        INSERT INTO app_lm_subprocessor_objection (workspace_id, notice_id, objected_by_user_id, reason)
                        VALUES (:workspaceId, :noticeId, :userId, :reason)
                        ON CONFLICT (workspace_id, notice_id) DO UPDATE
                            SET reason = EXCLUDED.reason, objected_by_user_id = EXCLUDED.objected_by_user_id,
                                objected_at = now()
                        """)
                .param("workspaceId", workspaceId)
                .param("noticeId", noticeId)
                .param("userId", userId)
                .param("reason", reason)
                .update();
    }

    private static SubprocessorObjection objectionOf(ResultSet row, int rowNumber) throws SQLException {
        return new SubprocessorObjection(row.getObject("notice_id", UUID.class),
                row.getObject("workspace_id", UUID.class), row.getString("objected_by_name"),
                row.getString("reason"), row.getTimestamp("objected_at").toInstant());
    }
}
