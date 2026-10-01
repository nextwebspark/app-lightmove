package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.CandidateTag;
import app.lightmove.api.candidate.model.CandidateTagUsage;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** A workspace's tag catalog. Every finder takes the workspace id: a tag is tenant data. */
public interface CandidateTagRepository extends JpaRepository<CandidateTag, UUID> {

    List<CandidateTag> findByWorkspaceIdOrderByLabelAsc(UUID workspaceId);

    List<CandidateTag> findByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);

    Optional<CandidateTag> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceId(UUID workspaceId);

    @Query("select t from CandidateTag t where t.workspaceId = :workspaceId and lower(t.label) = lower(:label)")
    Optional<CandidateTag> findByLabel(UUID workspaceId, String label);

    /** How many of the workspace's people hold each tag; a tag nobody holds is absent. */
    @Query(value = """
            SELECT pt.tag_id AS tagId, count(*) AS holders
            FROM app_lm_person_tag pt
            JOIN app_lm_person p ON p.id = pt.person_id
            WHERE p.workspace_id = :workspaceId
            GROUP BY pt.tag_id
            """, nativeQuery = true)
    List<CandidateTagUsage> usageOf(UUID workspaceId);

    default CandidateTag requireInWorkspace(UUID id, UUID workspaceId) {
        return findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** Seeds one starter tag; a second seeding racing this one, or an admin's own tag of that name, wins. */
    @Modifying
    @Query(value = """
            INSERT INTO app_lm_workspace_candidate_tag (workspace_id, label, colour)
            VALUES (:workspaceId, :label, :colour)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID workspaceId, String label, String colour);
}
