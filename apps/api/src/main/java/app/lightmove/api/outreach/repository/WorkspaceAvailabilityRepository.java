package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.WorkspaceAvailability;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Workspaces' default working weeks and email caps. Every read carries the workspace. */
public interface WorkspaceAvailabilityRepository extends JpaRepository<WorkspaceAvailability, UUID> {

    Optional<WorkspaceAvailability> findByWorkspaceId(UUID workspaceId);

    /** An empty row where there is none, waiting on a racing insert rather than failing beside it. */
    @Modifying
    @Query(value = "INSERT INTO app_lm_workspace_availability (workspace_id) VALUES (:workspaceId) "
            + "ON CONFLICT (workspace_id) DO NOTHING", nativeQuery = true)
    void insertIfMissing(UUID workspaceId);
}
