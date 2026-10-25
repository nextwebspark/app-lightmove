package app.lightmove.api.notetaker.repository;

import app.lightmove.api.notetaker.model.InterviewPlan;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Interview plans, every read carrying the workspace and the position the plan was made on. */
public interface InterviewPlanRepository extends JpaRepository<InterviewPlan, UUID> {

    Optional<InterviewPlan> findByWorkspaceIdAndProjectIdAndPersonIdAndStartsAtAndTitleKey(
            UUID workspaceId, UUID projectId, UUID personId, Instant startsAt, String titleKey);

    List<InterviewPlan> findByWorkspaceIdAndProjectIdAndPersonId(UUID workspaceId, UUID projectId, UUID personId);

    /** One Prepare questions press at a time per call: two presses ask the model once. */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE app_lm_interview_plan SET drafting_since = :now
            WHERE id = :id AND (drafting_since IS NULL OR drafting_since < :staleBefore)
            """)
    int claimDrafting(UUID id, Instant now, Instant staleBefore);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE app_lm_interview_plan SET drafting_since = NULL WHERE id = :id")
    int releaseDrafting(UUID id);
}
