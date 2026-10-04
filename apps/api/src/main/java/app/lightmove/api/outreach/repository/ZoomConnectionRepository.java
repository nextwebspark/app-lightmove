package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.ZoomConnection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultants' own Zoom accounts. Every read carries the workspace and the consultant. */
public interface ZoomConnectionRepository extends JpaRepository<ZoomConnection, UUID> {

    Optional<ZoomConnection> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
