package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.ExitReason;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** A workspace's reasons for leaving the process. Every finder takes the workspace id: a reason is tenant data. */
public interface ExitReasonRepository extends JpaRepository<ExitReason, UUID> {

    @EntityGraph(attributePaths = "statusIds")
    List<ExitReason> findByWorkspaceIdOrderByPositionAsc(UUID workspaceId);

    boolean existsByWorkspaceId(UUID workspaceId);

    boolean existsByWorkspaceIdAndReasonKey(UUID workspaceId, String reasonKey);

    Optional<ExitReason> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("select r from ExitReason r where r.workspaceId = :workspaceId and lower(r.label) = lower(:label)")
    Optional<ExitReason> findByLabel(UUID workspaceId, String label);

    /** Seeds V227's starting list where a workspace has no reason at all; a no-op for one that has any. */
    @Query(value = "SELECT CAST(app_lm_seed_exit_reasons(:workspaceId) AS text)", nativeQuery = true)
    String seedIfAbsent(UUID workspaceId);

    default ExitReason requireInWorkspace(UUID id, UUID workspaceId) {
        return findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
