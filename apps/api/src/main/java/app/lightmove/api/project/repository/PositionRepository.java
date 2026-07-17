package app.lightmove.api.project.repository;

import app.lightmove.api.project.model.Position;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Positions are only ever reached through their project, which the service has already scoped to
 * the caller's workspace — so no workspace-scoped finder is needed here.
 */
public interface PositionRepository extends JpaRepository<Position, UUID> {

    Optional<Position> findByProjectId(UUID projectId);
}
