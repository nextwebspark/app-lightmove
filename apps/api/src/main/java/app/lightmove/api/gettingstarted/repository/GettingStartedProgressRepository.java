package app.lightmove.api.gettingstarted.repository;

import app.lightmove.api.gettingstarted.model.GettingStartedProgress;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GettingStartedProgressRepository extends JpaRepository<GettingStartedProgress, UUID> {

    Optional<GettingStartedProgress> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /** Two tabs opening My positions at once both reach here; the unique key makes the second a no-op. */
    @Modifying
    @Query(value = """
            INSERT INTO app_lm_getting_started (workspace_id, user_id) VALUES (:workspaceId, :userId)
            ON CONFLICT (workspace_id, user_id) DO NOTHING
            """, nativeQuery = true)
    void ensureExists(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

    /** The existing stamps are on the right of {@code ||}, so a step's first-seen time is never moved. */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE app_lm_getting_started
            SET completed_steps = CAST(:stamps AS jsonb) || completed_steps
            WHERE workspace_id = :workspaceId AND user_id = :userId
            """, nativeQuery = true)
    void stampCompleted(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId,
                        @Param("stamps") String stamps);
}
