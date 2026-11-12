package app.lightmove.api.enrichment.sourcing.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.enrichment.sourcing.constant.SourcingRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One Find executives run. The companies and the search are frozen at request time and the outcomes grow one per
 * company as each finishes, so the panel can show progress and read the results back after a reload.
 */
@Entity
@Table(name = "app_lm_executive_sourcing_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutiveSourcingRun extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SourcingRunStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "companies", nullable = false, updatable = false)
    private List<SourcingCompany> companies = new ArrayList<>();

    /** The search as it was pressed; null on runs from before the panel. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criteria", updatable = false)
    private SearchCriteria criteria;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outcomes", nullable = false)
    private List<CompanyOutcome> outcomes = new ArrayList<>();

    /**
     * Each company's step while it is searched, keyed by triage company id. Written only by
     * {@code ExecutiveSourcingRunRepository.reportProgress}, never through the entity, so a company's step and
     * another's outcome never overwrite each other.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "progress", nullable = false, insertable = false, updatable = false)
    private Map<UUID, CompanyProgress> progress = new LinkedHashMap<>();

    @Column(name = "companies_done", nullable = false)
    private int companiesDone;

    @Column(name = "vendor_hits", nullable = false)
    private int vendorHits;

    /** Hits the people cache answered — returned free, where {@link #vendorHits} were bought. */
    @Column(name = "cached_hits", nullable = false)
    private int cachedHits;

    /** People found who fit the role, across the companies. */
    @Column(name = "suggested", nullable = false)
    private int suggested;

    @Column(name = "stop_requested_at")
    private Instant stopRequestedAt;

    @Column(name = "error")
    private String error;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public static ExecutiveSourcingRun requested(UUID workspaceId, UUID projectId, UUID requestedBy,
                                                 List<SourcingCompany> companies, SearchCriteria criteria) {
        ExecutiveSourcingRun run = new ExecutiveSourcingRun();
        run.workspaceId = workspaceId;
        run.projectId = projectId;
        run.requestedBy = requestedBy;
        run.status = SourcingRunStatus.QUEUED;
        run.companies = List.copyOf(companies);
        run.criteria = criteria;
        return run;
    }

    public void start() {
        status = SourcingRunStatus.RUNNING;
        startedAt = Instant.now();
    }

    public void recordOutcome(CompanyOutcome outcome) {
        List<CompanyOutcome> grown = new ArrayList<>(outcomes);
        grown.add(outcome);
        outcomes = grown;
        companiesDone = grown.size();
        vendorHits += outcome.vendorHits();
        cachedHits += outcome.cachedHits();
        suggested += outcome.fits();
    }

    /** Ends the run: stopped when a stop was asked for while it ran, completed otherwise. */
    public void finish() {
        status = stopRequestedAt == null ? SourcingRunStatus.COMPLETED : SourcingRunStatus.STOPPED;
        finishedAt = Instant.now();
    }

    /** False when the run had already ended, so there was nothing to stop. */
    public boolean requestStop() {
        if (!SourcingRunStatus.IN_PROGRESS.contains(status)) {
            return false;
        }
        if (stopRequestedAt == null) {
            stopRequestedAt = Instant.now();
        }
        return true;
    }

    public boolean isStopRequested() {
        return stopRequestedAt != null;
    }

    public void fail(String reason) {
        status = SourcingRunStatus.FAILED;
        error = reason;
        finishedAt = Instant.now();
    }

    /** Still marked in progress past {@code cutoff}: its worker died with the instance and will never finish it. */
    public boolean isLostBefore(Instant cutoff) {
        return SourcingRunStatus.IN_PROGRESS.contains(status) && getCreatedAt().isBefore(cutoff);
    }
}
