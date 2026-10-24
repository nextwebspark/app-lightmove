package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.WorkspaceAvailability;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Workspaces' default working weeks and email caps. Every read carries the workspace. */
public interface WorkspaceAvailabilityRepository extends JpaRepository<WorkspaceAvailability, UUID> {

    Optional<WorkspaceAvailability> findByWorkspaceId(UUID workspaceId);
}
