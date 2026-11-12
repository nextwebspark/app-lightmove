package app.lightmove.api.enrichment.sourcing.repository;

import app.lightmove.api.enrichment.sourcing.constant.SourcingRunStatus;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRun;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** A mandate's runs. Every finder carries the project — a run is mandate content. */
public interface ExecutiveSourcingRunRepository extends JpaRepository<ExecutiveSourcingRun, UUID> {

    Optional<ExecutiveSourcingRun> findByIdAndWorkspaceIdAndProjectId(UUID id, UUID workspaceId, UUID projectId);

    Optional<ExecutiveSourcingRun> findFirstByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(UUID workspaceId,
                                                                                         UUID projectId);

    List<ExecutiveSourcingRun> findByWorkspaceIdAndProjectIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID workspaceId,
                                                                                              UUID projectId,
                                                                                              Instant after);

    /**
     * Asks a run in progress to stop without touching its version, so a company's outcome being recorded at the
     * same moment never fails on it, nor the stop on the outcome.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update ExecutiveSourcingRun run set run.stopRequestedAt = :at
            where run.id = :runId and run.stopRequestedAt is null and run.status in :inProgress
            """)
    int markStopRequested(@Param("runId") UUID runId, @Param("at") Instant at,
                          @Param("inProgress") Collection<SourcingRunStatus> inProgress);

    List<ExecutiveSourcingRun> findByWorkspaceIdAndProjectIdAndStatusIn(UUID workspaceId, UUID projectId,
                                                                        Collection<SourcingRunStatus> statuses);
}
