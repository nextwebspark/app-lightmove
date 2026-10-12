package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.CandidateStage;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** A workspace's pipeline stages. Every finder takes the workspace id: a stage is tenant data. */
public interface CandidateStageRepository extends JpaRepository<CandidateStage, UUID> {

    List<CandidateStage> findByWorkspaceIdOrderByPositionAsc(UUID workspaceId);

    Optional<CandidateStage> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceId(UUID workspaceId);

    boolean existsByWorkspaceIdAndStageKey(UUID workspaceId, String stageKey);

    @Query("select s from CandidateStage s where s.workspaceId = :workspaceId and lower(s.label) = lower(:label)")
    Optional<CandidateStage> findByLabel(UUID workspaceId, String label);

    /** Seeds V130's starting catalog where a workspace has none; a no-op for one already seeded. */
    @Query(value = "SELECT CAST(app_lm_seed_candidate_pipeline(:workspaceId) AS text)", nativeQuery = true)
    String seedIfAbsent(UUID workspaceId);

    default CandidateStage requireInWorkspace(UUID id, UUID workspaceId) {
        return findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
