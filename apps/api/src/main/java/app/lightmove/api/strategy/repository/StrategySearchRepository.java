package app.lightmove.api.strategy.repository;

import app.lightmove.api.strategy.constant.SearchVisibility;
import app.lightmove.api.strategy.model.StrategySearch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Saved searches are reached only through their already-scoped project, so these finders carry a
 * project id rather than a workspace id — the same exception {@code StrategyRepository} documents.
 * The service resolves the project against the caller's workspace before ever coming here.
 */
public interface StrategySearchRepository extends JpaRepository<StrategySearch, UUID> {

    /**
     * The dropdown's read: the mandate's shared searches plus the caller's own private ones. Other
     * people's private searches are excluded here rather than filtered afterwards, so no code path
     * can hold one and then forget to.
     */
    default List<StrategySearch> findVisibleTo(UUID projectId, UUID userId) {
        return findVisible(projectId, userId, SearchVisibility.SHARED);
    }

    @Query("""
           select s from StrategySearch s
           where s.projectId = :projectId
             and (s.visibility = :shared or s.createdBy = :userId)
           order by s.name asc
           """)
    List<StrategySearch> findVisible(@Param("projectId") UUID projectId,
                                     @Param("userId") UUID userId,
                                     @Param("shared") SearchVisibility shared);

    long countByProjectIdAndVisibility(UUID projectId, SearchVisibility visibility);

    long countByProjectIdAndCreatedByAndVisibility(UUID projectId, UUID createdBy,
                                                   SearchVisibility visibility);

    /** Deliberately unfiltered by visibility — whose row this is, is the service's decision. */
    Optional<StrategySearch> findByIdAndProjectId(UUID id, UUID projectId);
}
