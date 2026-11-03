package app.lightmove.api.enrichment.sourcing.repository;

import app.lightmove.api.enrichment.sourcing.model.SourcingCriteria;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** A position's saved search. Every finder carries the workspace and the project. */
public interface SourcingCriteriaRepository extends JpaRepository<SourcingCriteria, UUID> {

    Optional<SourcingCriteria> findByWorkspaceIdAndProjectId(UUID workspaceId, UUID projectId);
}
