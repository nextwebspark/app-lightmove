package app.lightmove.api.enrichment.sourcing.repository;

import app.lightmove.api.enrichment.sourcing.constant.SourcingRunStatus;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRun;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** A mandate's runs. Every finder carries the project — a run is mandate content. */
public interface ExecutiveSourcingRunRepository extends JpaRepository<ExecutiveSourcingRun, UUID> {

    Optional<ExecutiveSourcingRun> findByIdAndWorkspaceIdAndProjectId(UUID id, UUID workspaceId, UUID projectId);

    Optional<ExecutiveSourcingRun> findFirstByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(UUID workspaceId,
                                                                                         UUID projectId);

    List<ExecutiveSourcingRun> findByWorkspaceIdAndProjectIdAndStatusIn(UUID workspaceId, UUID projectId,
                                                                        Collection<SourcingRunStatus> statuses);
}
