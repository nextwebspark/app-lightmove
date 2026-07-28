package app.lightmove.api.project.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.project.constant.SourcingRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One project's CoreSignal sourcing run — the search result (ordered ids + provider total) and how
 * deep into it the project has paid to collect. One row per project, replaced in place when the
 * strategy criteria change: history buys nothing for the POC, and the unique {@code project_id}
 * makes "the current run" a plain lookup.
 *
 * <p>Deliberately no collected-count column: progress is derived at read time from cache
 * membership, so the parallel collectors never write (and never contend on) this row mid-run.
 */
@Entity
@Table(name = "app_lm_coresignal_sourcing_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourcingRun extends BaseEntity {

    @Column(name = "project_id", nullable = false, unique = true, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourcingRunStatus status;

    @Column(name = "criteria_hash", nullable = false)
    private String criteriaHash;

    /** CoreSignal ids in the provider's revenue-desc order — fixed at search time, never reshuffled. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "searched_ids", nullable = false)
    private List<Long> searchedIds;

    @Column(name = "total_matched", nullable = false)
    private long totalMatched;

    /** How deep into {@link #searchedIds} this project has paid to collect; grows one batch per extend. */
    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Column(name = "error_detail")
    private String errorDetail;

    public static SourcingRun start(UUID projectId, String criteriaHash, int requestedCount) {
        SourcingRun run = new SourcingRun();
        run.projectId = projectId;
        run.reset(criteriaHash, requestedCount);
        return run;
    }

    /** Reuse the row for changed criteria (or a retry): back to square one, previous search discarded. */
    public void restartWith(String criteriaHash, int requestedCount) {
        reset(criteriaHash, requestedCount);
    }

    private void reset(String criteriaHash, int requestedCount) {
        this.status = SourcingRunStatus.PENDING;
        this.criteriaHash = criteriaHash;
        this.searchedIds = List.of();
        this.totalMatched = 0;
        this.requestedCount = requestedCount;
        this.errorDetail = null;
    }

    public void markSearching() {
        this.status = SourcingRunStatus.SEARCHING;
    }

    /** The search answered: fix the order, clamp the first batch to what actually exists. */
    public void storeSearchResults(List<Long> ids, long totalMatched) {
        this.searchedIds = List.copyOf(ids);
        this.totalMatched = totalMatched;
        this.requestedCount = Math.min(requestedCount, ids.size());
        this.status = SourcingRunStatus.COLLECTING;
    }

    /** "Load more": pay for one more batch, clamped to the ids the search actually returned. */
    public void extendBy(int increment) {
        this.requestedCount = Math.min(requestedCount + increment, searchedIds.size());
        this.status = SourcingRunStatus.COLLECTING;
    }

    public void markReady() {
        this.status = SourcingRunStatus.READY;
    }

    public void markFailed(String detail) {
        this.status = SourcingRunStatus.FAILED;
        this.errorDetail = detail;
    }
}
