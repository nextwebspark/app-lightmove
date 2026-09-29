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
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One Find executives run. The companies are frozen at request time and the outcomes grow one per
 * company as each finishes, so the strip can show progress and the summary reads back after a reload.
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spec")
    private SourcingSpec spec;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outcomes", nullable = false)
    private List<CompanyOutcome> outcomes = new ArrayList<>();

    @Column(name = "companies_done", nullable = false)
    private int companiesDone;

    @Column(name = "executives_filed", nullable = false)
    private int executivesFiled;

    @Column(name = "vendor_hits", nullable = false)
    private int vendorHits;

    /** Hits the people cache answered — returned free, where {@link #vendorHits} were bought. */
    @Column(name = "cached_hits", nullable = false)
    private int cachedHits;

    @Column(name = "model_calls", nullable = false)
    private int modelCalls;

    @Column(name = "error")
    private String error;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public static ExecutiveSourcingRun requested(UUID workspaceId, UUID projectId, UUID requestedBy,
                                                 List<SourcingCompany> companies) {
        ExecutiveSourcingRun run = new ExecutiveSourcingRun();
        run.workspaceId = workspaceId;
        run.projectId = projectId;
        run.requestedBy = requestedBy;
        run.status = SourcingRunStatus.QUEUED;
        run.companies = List.copyOf(companies);
        return run;
    }

    public void start() {
        status = SourcingRunStatus.RUNNING;
        startedAt = Instant.now();
    }

    public void recordSpec(SourcingSpec searched) {
        spec = searched;
    }

    public void recordOutcome(CompanyOutcome outcome) {
        List<CompanyOutcome> grown = new ArrayList<>(outcomes);
        grown.add(outcome);
        outcomes = grown;
        companiesDone = grown.size();
        executivesFiled += outcome.filed();
        vendorHits += outcome.vendorHits();
        cachedHits += outcome.cachedHits();
        modelCalls += outcome.modelCalls();
    }

    public void countModelCall() {
        modelCalls += 1;
    }

    public void finish() {
        status = SourcingRunStatus.COMPLETED;
        finishedAt = Instant.now();
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
