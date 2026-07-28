package app.lightmove.api.project.service;

import app.lightmove.api.company.model.CoreSignalSearchCriteria;
import app.lightmove.api.company.model.CoreSignalSearchResult;
import app.lightmove.api.company.service.CoreSignalCompanyCache;
import app.lightmove.api.company.service.CoreSignalGateway;
import app.lightmove.api.company.service.CoreSignalUnavailableException;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.project.constant.SourcingRunStatus;
import app.lightmove.api.project.model.SourcingRun;
import app.lightmove.api.project.repository.SourcingRunRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs one CoreSignal sourcing run off-request: search once, then collect the requested batch in
 * parallel, each hit landing in the shared cache as its own committed transaction so the polling
 * read streams results the moment they exist.
 *
 * <p>A separate bean from {@link SourcingRunService} because {@code @Async} is proxy-based — the
 * same self-invocation trap {@code AuditEventWriter} documents. Deliberately NOT
 * {@code @Transactional} at this level: each {@code runs.save} commits alone (repository-level
 * transaction), so status transitions become visible to pollers immediately, and the collect loop
 * writes through {@link CoreSignalCompanyCache}'s own {@code REQUIRES_NEW} boundary.
 *
 * <p>Idempotent by construction — it re-derives what is missing from cache membership — so a
 * duplicate kick (double-POST race, extend-after-ready) converges instead of double-spending.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SourcingRunExecutor {

    private final SourcingRunRepository runs;
    private final SourcingCriteriaResolver resolver;
    private final CoreSignalGateway gateway;
    private final CoreSignalCompanyCache cache;
    private final LightMoveProperties properties;

    @Async
    public void execute(UUID runId) {
        SourcingRun run = runs.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        try {
            // Every save returns the merged copy with the bumped @Version — the entity is detached
            // between transitions out here, so keeping the old reference would go stale and the
            // next save would throw ObjectOptimisticLockingFailureException.
            if (run.getStatus() == SourcingRunStatus.PENDING) {
                run = search(run);
                if (run == null) {
                    return;
                }
            }
            collectMissing(run);
        } catch (CoreSignalUnavailableException ex) {
            log.warn("Sourcing run {} failed: {}", runId, ex.getMessage());
            fail(runId, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Sourcing run {} failed unexpectedly", runId, ex);
            fail(runId, "Unexpected failure: " + ex.getMessage());
        }
    }

    /** One search request fixes the result order for the run's whole life. Null = nothing to collect. */
    private SourcingRun search(SourcingRun run) {
        CoreSignalSearchCriteria criteria = resolver.resolveForProject(run.getProjectId()).toCriteria();

        // An anchorless scope matches nothing by definition (mirrors the local engine's rule) —
        // and an unanchored provider search is a credit bomb. READY with zero results, no call.
        if (!criteria.hasAnchor()) {
            run.storeSearchResults(List.of(), 0);
            run.markReady();
            runs.save(run);
            return null;
        }

        run.markSearching();
        run = runs.save(run);
        CoreSignalSearchResult result =
                gateway.searchCompanyIds(criteria, properties.coresignal().maxSearchIds());
        run.storeSearchResults(result.ids(), result.totalMatched());
        return runs.save(run);
    }

    /** Failure lands on a freshly loaded row — the in-flight reference may hold a stale version. */
    private void fail(UUID runId, String detail) {
        runs.findById(runId).ifPresent(fresh -> {
            fresh.markFailed(detail);
            runs.save(fresh);
        });
    }

    private void collectMissing(SourcingRun run) {
        List<Long> requested = run.getSearchedIds().subList(0, run.getRequestedCount());
        Set<Long> cached = cache.cachedIds(requested);
        List<Long> missing = requested.stream().filter(id -> !cached.contains(id)).toList();

        if (!missing.isEmpty()) {
            log.info("Sourcing run {}: collecting {} of {} requested ({} already cached)",
                    run.getId(), missing.size(), requested.size(), cached.size());
            collectInParallel(run, missing);
        }
        run.markReady();
        runs.save(run);
    }

    /**
     * Parallel collects on virtual threads — well under CoreSignal's 54 req/s collect limit at the
     * batch sizes configured here. A fatal failure (bad key, no credits) aborts the run: every
     * remaining call would fail identically. A transient per-id failure only skips that company —
     * unless every collect failed, which is a dead provider wearing a transient mask.
     */
    private void collectInParallel(SourcingRun run, List<Long> missing) {
        AtomicReference<CoreSignalUnavailableException> fatal = new AtomicReference<>();
        AtomicInteger failures = new AtomicInteger();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<? extends Future<?>> futures = missing.stream()
                    .map(id -> executor.submit(() -> collectOne(id, fatal, failures)))
                    .toList();
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new CoreSignalUnavailableException("Sourcing run interrupted", true, ex);
                } catch (ExecutionException ex) {
                    // collectOne handles its own failures; anything escaping it is a programming error.
                    log.error("Collect task for run {} threw unexpectedly", run.getId(), ex.getCause());
                }
            }
        }

        if (fatal.get() != null) {
            throw fatal.get();
        }
        // Not "collected == 0": a batch of vanished ids (all 404s) is a valid, empty outcome. Only
        // every-single-call-erroring means the provider is down wearing a transient mask.
        if (failures.get() == missing.size()) {
            throw new CoreSignalUnavailableException(
                    "Every collect in the batch failed — provider unreachable or rejecting requests", true);
        }
    }

    private void collectOne(long coresignalId, AtomicReference<CoreSignalUnavailableException> fatal,
                            AtomicInteger failures) {
        if (fatal.get() != null) {
            return;
        }
        try {
            gateway.collect(coresignalId).ifPresent(cache::store);
        } catch (CoreSignalUnavailableException ex) {
            if (ex.isFatal()) {
                fatal.compareAndSet(null, ex);
            } else {
                failures.incrementAndGet();
                log.warn("Skipping CoreSignal company {}: {}", coresignalId, ex.getMessage());
            }
        }
    }
}
