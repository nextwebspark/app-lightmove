package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.model.CandidateStatusUsage;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** A workspace's candidate statuses. Every finder takes the workspace id: a status is tenant data. */
public interface CandidateStatusDefinitionRepository extends JpaRepository<CandidateStatusDefinition, UUID> {

    @Query("select s from CandidateStatusDefinition s join fetch s.stage where s.workspaceId = :workspaceId")
    List<CandidateStatusDefinition> findCatalogOf(UUID workspaceId);

    Optional<CandidateStatusDefinition> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndStatusKey(UUID workspaceId, String statusKey);

    @Query("select s from CandidateStatusDefinition s where s.workspaceId = :workspaceId and lower(s.label) = lower(:label)")
    Optional<CandidateStatusDefinition> findByLabel(UUID workspaceId, String label);

    /** How many of the workspace's mapped executives hold each status; a status nobody holds is absent. */
    @Query(value = """
            SELECT c.status_id AS statusId, count(*) AS holders
            FROM app_lm_project_candidate c
            JOIN app_lm_project p ON p.id = c.project_id
            WHERE p.workspace_id = :workspaceId
            GROUP BY c.status_id
            """, nativeQuery = true)
    List<CandidateStatusUsage> usageOf(UUID workspaceId);

    default CandidateStatusDefinition requireInWorkspace(UUID id, UUID workspaceId) {
        return findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
