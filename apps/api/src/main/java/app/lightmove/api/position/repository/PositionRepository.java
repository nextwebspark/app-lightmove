package app.lightmove.api.position.repository;

import app.lightmove.api.position.model.Position;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Positions are only ever reached through their project, which the service has already scoped to
 * the caller's workspace — so no workspace-scoped finder is needed here.
 */
public interface PositionRepository extends JpaRepository<Position, UUID> {

    Optional<Position> findByProjectId(UUID projectId);

    /** A drafted brief is inserted at version 0; any save of a step, or publishing it, is someone's work. */
    @Query("select p.projectId from Position p where p.projectId in :projectIds and (p.version > 0 or p.publishedAt is not null)")
    List<UUID> findProjectIdsWorkedOn(@Param("projectIds") Collection<UUID> projectIds);
}
