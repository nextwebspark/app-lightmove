package app.uncava.api.strategy.repository;

import app.uncava.api.strategy.model.Strategy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reached only through a project the service has already scoped to the caller's workspace. */
public interface StrategyRepository extends JpaRepository<Strategy, UUID> {

    Optional<Strategy> findByProjectId(UUID projectId);
}
