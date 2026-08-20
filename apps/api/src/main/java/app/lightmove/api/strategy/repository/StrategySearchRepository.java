package app.lightmove.api.strategy.repository;

import app.lightmove.api.strategy.model.StrategySearch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Saved searches are reached only through their already-scoped project, so these finders carry a
 * project id rather than a workspace id — the same exception {@code StrategyRepository} documents.
 * The service resolves the project against the caller's workspace before ever coming here.
 */
public interface StrategySearchRepository extends JpaRepository<StrategySearch, UUID> {

    List<StrategySearch> findByProjectIdOrderByNameAsc(UUID projectId);

    Optional<StrategySearch> findByIdAndProjectId(UUID id, UUID projectId);
}
