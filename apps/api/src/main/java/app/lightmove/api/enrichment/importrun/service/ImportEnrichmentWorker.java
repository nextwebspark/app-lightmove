package app.lightmove.api.enrichment.importrun.service;

import app.lightmove.api.core.concurrent.service.ThreadContextPropagation;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.SpreadsheetImportSettings;
import app.lightmove.api.core.tenant.EveryWorkspace;
import app.lightmove.api.core.tenant.TenantResolvers;
import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentOutcome;
import app.lightmove.api.enrichment.importrun.model.ClaimedEnrichmentItem;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the rows imports queued for enrichment, every workspace's claim at once on one shared pool. A persisted queue
 * rather than events: the shared {@code @Async} pool has no queue and overflows onto the publisher's thread.
 */
@Component
@Slf4j
public class ImportEnrichmentWorker {

    private final ImportEnrichmentItemStore store;
    private final ImportEnrichmentResearch research;
    private final TenantResolvers resolvers;
    private final EveryWorkspace workspaces;
    private final Clock clock;
    private final SpreadsheetImportSettings settings;
    private final ExecutorService pool = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("import-enrichment-", 0).factory());
    private final Semaphore reads;
    private final AtomicInteger inFlight = new AtomicInteger();

    ImportEnrichmentWorker(ImportEnrichmentItemStore store, ImportEnrichmentResearch research,
                           TenantResolvers resolvers, EveryWorkspace workspaces, Clock clock,
                           LightMoveProperties properties) {
        this.store = store;
        this.research = research;
        this.resolvers = resolvers;
        this.workspaces = workspaces;
        this.clock = clock;
        this.settings = properties.spreadsheetImport();
        this.reads = new Semaphore(settings.enrichmentParallelism());
    }

    /**
     * Claims nothing while an earlier tick's rows are still being read, so a claim never outlives its tick long enough
     * to go stale and be taken twice; waits for its own up to half the stale window.
     */
    @Scheduled(fixedDelayString = "${lightmove.spreadsheet-import.enrichment-tick}",
            initialDelayString = "${lightmove.spreadsheet-import.enrichment-tick}")
    public void drain() {
        if (inFlight.get() > 0) {
            return;
        }
        Instant staleBefore = clock.instant().minus(settings.enrichmentStaleAfter());
        List<Future<?>> claimed = new ArrayList<>();
        workspaces.forEach(resolvers.workspacesWithImportEnrichmentDue(staleBefore), workspaceId -> {
            store.giveUpStale(workspaceId, staleBefore);
            for (ClaimedEnrichmentItem item : store.claim(workspaceId, staleBefore, settings.enrichmentBatch())) {
                inFlight.incrementAndGet();
                claimed.add(pool.submit(ThreadContextPropagation.wrap(() -> researchOne(item))));
            }
        });
        awaitWithin(claimed, settings.enrichmentStaleAfter().dividedBy(2));
    }

    private void researchOne(ClaimedEnrichmentItem item) {
        try {
            reads.acquireUninterruptibly();
            ImportEnrichmentOutcome outcome;
            try {
                outcome = research.research(item);
            } catch (RuntimeException failed) {
                // The message only: a transport failure's cause can quote the profile address.
                log.warn("Import enrichment of {} {} failed: {}", item.kind(), item.targetId(), failed.getMessage());
                outcome = ImportEnrichmentOutcome.FAILED;
            } finally {
                reads.release();
            }
            store.finish(item.workspaceId(), item.id(), outcome);
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private static void awaitWithin(List<Future<?>> claimed, Duration budget) {
        long deadline = System.nanoTime() + budget.toNanos();
        for (Future<?> read : claimed) {
            try {
                read.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException overBudget) {
                return;
            } catch (ExecutionException failed) {
                log.warn("An import enrichment read ended abnormally: {}", failed.getCause().toString());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @PreDestroy
    void stop() {
        pool.shutdownNow();
    }
}
