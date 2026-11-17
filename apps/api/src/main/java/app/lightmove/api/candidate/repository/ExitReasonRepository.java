package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.ExitReason;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** A workspace's reasons for leaving the process. Every finder takes the workspace id: a reason is tenant data. */
public interface ExitReasonRepository extends JpaRepository<ExitReason, UUID> {

    @EntityGraph(attributePaths = "statusIds")
    List<ExitReason> findByWorkspaceIdOrderByPositionAsc(UUID workspaceId);

    boolean existsByWorkspaceIdAndReasonKey(UUID workspaceId, String reasonKey);

    Optional<ExitReason> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("select r from ExitReason r where r.workspaceId = :workspaceId and lower(r.label) = lower(:label)")
    Optional<ExitReason> findByLabel(UUID workspaceId, String label);

    /** A status brought back into process offers no reason (the rule is kept, not just checked on a write). */
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM app_lm_workspace_status_exit_reason WHERE status_id = :statusId", nativeQuery = true)
    int withdrawFromStatus(UUID statusId);

    default ExitReason requireInWorkspace(UUID id, UUID workspaceId) {
        return findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
