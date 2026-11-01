package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.constant.SuppressionKind;
import app.lightmove.api.candidate.model.WorkspaceSuppression;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** A workspace's suppression list. Every finder takes the workspace id: the list is tenant data. */
public interface WorkspaceSuppressionRepository extends JpaRepository<WorkspaceSuppression, UUID> {

    List<WorkspaceSuppression> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<WorkspaceSuppression> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<WorkspaceSuppression> findByWorkspaceIdAndKindAndValueHashIn(UUID workspaceId, SuppressionKind kind,
                                                                      Collection<String> valueHashes);

    Optional<WorkspaceSuppression> findByWorkspaceIdAndKindAndValueHash(UUID workspaceId, SuppressionKind kind,
                                                                        String valueHash);

    /** A key already on the list keeps its first reason and note: who suppressed it first is the record. */
    @Modifying
    @Query(value = """
            INSERT INTO app_lm_workspace_suppression (workspace_id, kind, value_hash, reason, note, created_by)
            VALUES (:workspaceId, :kind, :valueHash, :reason, :note, :createdBy)
            ON CONFLICT (workspace_id, kind, value_hash) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID workspaceId, String kind, String valueHash, String reason, String note, UUID createdBy);
}
