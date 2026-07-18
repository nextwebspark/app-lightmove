package app.lightmove.api.project.repository;

import app.lightmove.api.project.model.Strategy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Strategies are only ever reached through their project, which the service has already scoped to
 * the caller's workspace — so no workspace-scoped finder is needed here.
 */
public interface StrategyRepository extends JpaRepository<Strategy, UUID> {

    Optional<Strategy> findByProjectId(UUID projectId);
}
