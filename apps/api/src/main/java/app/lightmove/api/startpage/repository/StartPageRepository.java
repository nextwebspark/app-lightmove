package app.lightmove.api.startpage.repository;

import app.lightmove.api.startpage.model.StartPage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StartPageRepository extends JpaRepository<StartPage, UUID> {

    Optional<StartPage> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /** One statement, so two first saves at once (two tabs, a double click) both land rather than one tripping the key. */
    @Modifying
    @Query(value = """
            INSERT INTO app_lm_start_page (workspace_id, user_id, kind, dashboard_id, project_id, section)
            VALUES (:workspaceId, :userId, :kind, CAST(:dashboardId AS uuid), CAST(:projectId AS uuid), :section)
            ON CONFLICT (workspace_id, user_id) DO UPDATE
               SET kind = EXCLUDED.kind, dashboard_id = EXCLUDED.dashboard_id, project_id = EXCLUDED.project_id,
                   section = EXCLUDED.section, version = app_lm_start_page.version + 1""", nativeQuery = true)
    void upsert(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId, @Param("kind") String kind,
                @Param("dashboardId") String dashboardId, @Param("projectId") String projectId,
                @Param("section") String section);

    @Modifying
    @Query("DELETE FROM StartPage page WHERE page.workspaceId = :workspaceId AND page.userId = :userId")
    void deleteByMember(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);
}
