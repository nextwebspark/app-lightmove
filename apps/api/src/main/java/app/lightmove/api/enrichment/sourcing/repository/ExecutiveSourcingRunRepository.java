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

    List<ExecutiveSourcingRun> findByWorkspaceIdAndProjectIdAndStatusIn(UUID workspaceId, UUID projectId,
                                                                        Collection<SourcingRunStatus> statuses);

    /**
     * Merges one company's step into the run's progress in a single statement, keeping when it started: the
     * companies searched side by side each write their own key, and the row's version is left alone so an outcome
     * being recorded beside it never fails.
     */
    @Modifying
    @Query(value = """
            UPDATE app_lm_executive_sourcing_run
            SET progress = jsonb_set(progress, ARRAY[CAST(:companyId AS text)],
                    COALESCE(progress -> CAST(:companyId AS text), CAST('{}' AS jsonb))
                    || jsonb_build_object('stage', CAST(:stage AS text), 'considered', CAST(:considered AS integer),
                            'done', CAST(:done AS integer), 'total', CAST(:total AS integer),
                            'startedAtMillis', COALESCE(progress -> CAST(:companyId AS text) -> 'startedAtMillis',
                                    to_jsonb(CAST(floor(extract(epoch FROM clock_timestamp()) * 1000) AS bigint)))))
            WHERE id = :runId
            """, nativeQuery = true)
    int reportProgress(@Param("runId") UUID runId, @Param("companyId") UUID companyId, @Param("stage") String stage,
                       @Param("considered") int considered, @Param("done") int done, @Param("total") int total);

    /** Marks when a company finished, beside its last step; a company never started has no entry to mark. */
    @Modifying
    @Query(value = """
            UPDATE app_lm_executive_sourcing_run
            SET progress = jsonb_set(progress, ARRAY[CAST(:companyId AS text), 'finishedAtMillis'],
                    to_jsonb(CAST(floor(extract(epoch FROM clock_timestamp()) * 1000) AS bigint)))
            WHERE id = :runId AND progress -> CAST(:companyId AS text) IS NOT NULL
            """, nativeQuery = true)
    int markProgressFinished(@Param("runId") UUID runId, @Param("companyId") UUID companyId);
}
