package app.lightmove.api.enrichment.importrun.service;

import app.lightmove.api.core.concurrent.service.ThreadContextPropagation;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.SpreadsheetImportSettings;
import app.lightmove.api.core.tenant.EveryWorkspace;
import app.lightmove.api.core.tenant.TenantResolvers;
import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentKind;
import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentOutcome;
import app.lightmove.api.enrichment.importrun.model.ClaimedEnrichmentItem;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
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
    private final ImportPersonResearch people;
    private final ImportCompanyResearch companies;
    private final TenantResolvers resolvers;
    private final EveryWorkspace workspaces;
    private final Clock clock;
    private final SpreadsheetImportSettings settings;
    private final ExecutorService pool = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("import-enrichment-", 0).factory());
    private final Semaphore reads;
    private final AtomicInteger inFlight = new AtomicInteger();

    ImportEnrichmentWorker(ImportEnrichmentItemStore store, ImportPersonResearch people,
                           ImportCompanyResearch companies, TenantResolvers resolvers, EveryWorkspace workspaces,
                           Clock clock, LightMoveProperties properties) {
        this.store = store;
        this.people = people;
        this.companies = companies;
        this.resolvers = resolvers;
        this.workspaces = workspaces;
        this.clock = clock;
        this.settings = properties.spreadsheetImport();
        this.reads = new Semaphore(settings.enrichmentParallelism());
    }

    /**
     * Claims and hands the reads to the pool, then returns: the scheduler's threads are shared with every other job.
     * Claims nothing while an earlier tick's reads still run, so a claim never waits long enough to go stale.
     */
    @Scheduled(fixedDelayString = "${lightmove.spreadsheet-import.enrichment-tick}",
            initialDelayString = "${lightmove.spreadsheet-import.enrichment-tick}")
    public void drain() {
        if (inFlight.get() > 0) {
            return;
        }
        Instant staleBefore = clock.instant().minus(settings.enrichmentStaleAfter());
        workspaces.forEach(resolvers.workspacesWithImportEnrichmentDue(staleBefore), workspaceId -> {
            store.giveUpStale(workspaceId, staleBefore);
            for (ClaimedEnrichmentItem item : store.claim(workspaceId, staleBefore, settings.enrichmentBatch())) {
                inFlight.incrementAndGet();
                pool.execute(ThreadContextPropagation.wrap(() -> researchOne(item)));
            }
        });
    }

    /** No read is running: what a test waits for between drains. */
    public boolean isIdle() {
        return inFlight.get() == 0;
    }

    private void researchOne(ClaimedEnrichmentItem item) {
        try {
            finish(item, outcomeOf(item));
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private ImportEnrichmentOutcome outcomeOf(ClaimedEnrichmentItem item) {
        reads.acquireUninterruptibly();
        try {
            return item.kind() == ImportEnrichmentKind.PERSON ? people.research(item) : companies.research(item);
        } catch (ImportAllowanceSpent spent) {
            return ImportEnrichmentOutcome.OVER_ALLOWANCE;
        } catch (RuntimeException failed) {
            // The message only: a transport failure's cause can quote the profile address.
            log.warn("Import enrichment of {} {} failed: {}", item.kind(), item.targetId(), failed.getMessage());
            return ImportEnrichmentOutcome.FAILED;
        } finally {
            reads.release();
        }
    }

    /**
     * Retried once: a row left RUNNING is claimed again once stale, and a person already bought would be bought again
     * (the profile lands on file, so the retry is free, but a company miss is not).
     */
    private void finish(ClaimedEnrichmentItem item, ImportEnrichmentOutcome outcome) {
        try {
            store.finish(item.workspaceId(), item.id(), outcome);
        } catch (RuntimeException firstFailure) {
            try {
                store.finish(item.workspaceId(), item.id(), outcome);
            } catch (RuntimeException secondFailure) {
                log.error("Import enrichment row {} stayed RUNNING after its read: {}", item.id(),
                        secondFailure.toString());
            }
        }
    }

    @PreDestroy
    void stop() {
        pool.shutdownNow();
    }
}
