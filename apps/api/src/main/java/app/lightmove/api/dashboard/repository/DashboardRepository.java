package app.lightmove.api.dashboard.repository;

import app.lightmove.api.dashboard.model.Dashboard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DashboardRepository extends JpaRepository<Dashboard, UUID> {

    /** A position's dashboards the member may open: their own, and the ones shared with the position's staff. */
    @Query("""
            SELECT d FROM Dashboard d
            WHERE d.workspaceId = :workspaceId AND d.projectId = :projectId
              AND (d.ownerUserId = :userId OR d.shared = true)
            ORDER BY d.createdAt, d.id""")
    List<Dashboard> findVisibleOnProject(@Param("workspaceId") UUID workspaceId, @Param("projectId") UUID projectId,
                                         @Param("userId") UUID userId);

    /** The workspace's dashboards the member may open: their own, and the ones shared with its staff. */
    @Query("""
            SELECT d FROM Dashboard d
            WHERE d.workspaceId = :workspaceId AND d.projectId IS NULL
              AND (d.ownerUserId = :userId OR d.shared = true)
            ORDER BY d.createdAt, d.id""")
    List<Dashboard> findVisibleOnWorkspace(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

    Optional<Dashboard> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
