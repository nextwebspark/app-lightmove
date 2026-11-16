package app.lightmove.api.enrichment.importrun.service;

import app.lightmove.api.core.concurrent.service.ThreadContextPropagation;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.SpreadsheetImportSettings;
import app.lightmove.api.core.tenant.EveryWorkspace;
import app.lightmove.api.core.tenant.TenantResolvers;
import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentOutcome;
import app.lightmove.api.enrichment.importrun.model.ClaimedEnrichmentItem;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the rows spreadsheet imports queued for enrichment: a few per workspace per tick, researched side by side,
 * each finished in its own statement. Persisted rather than published as events because the shared {@code @Async} pool
 * has no queue — past its threads a listener runs on the publisher's, which would have put a 250-link file's vendor
 * reads on the import's own request thread — and because a row outlives the instance that claimed it.
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

    ImportEnrichmentWorker(ImportEnrichmentItemStore store, ImportEnrichmentResearch research,
                           TenantResolvers resolvers, EveryWorkspace workspaces, Clock clock,
                           LightMoveProperties properties) {
        this.store = store;
        this.research = research;
        this.resolvers = resolvers;
        this.workspaces = workspaces;
        this.clock = clock;
        this.settings = properties.spreadsheetImport();
    }

    @Scheduled(fixedDelayString = "${lightmove.spreadsheet-import.enrichment-tick}",
            initialDelayString = "${lightmove.spreadsheet-import.enrichment-tick}")
    public void drain() {
        Instant now = clock.instant();
        Instant staleBefore = now.minus(settings.enrichmentStaleAfter());
        workspaces.forEach(resolvers.workspacesWithImportEnrichmentDue(staleBefore),
                workspaceId -> drainWorkspace(workspaceId, now, staleBefore));
    }

    private void drainWorkspace(UUID workspaceId, Instant now, Instant staleBefore) {
        store.giveUpStale(workspaceId, staleBefore);
        List<ClaimedEnrichmentItem> claimed = store.claim(workspaceId, staleBefore, settings.enrichmentBatch());
        try (ExecutorService pool = Executors.newFixedThreadPool(settings.enrichmentParallelism(),
                Thread.ofVirtual().factory())) {
            claimed.forEach(item -> pool.execute(ThreadContextPropagation.wrap(() -> researchOne(item))));
        }
        store.purgeFinishedBefore(workspaceId, now.minus(settings.enrichmentKeepFor()));
    }

    private void researchOne(ClaimedEnrichmentItem item) {
        ImportEnrichmentOutcome outcome;
        try {
            outcome = research.research(item);
        } catch (RuntimeException failed) {
            // The message only: a transport failure's cause can quote the profile address.
            log.warn("Import enrichment of {} {} failed: {}", item.kind(), item.targetId(), failed.getMessage());
            outcome = ImportEnrichmentOutcome.FAILED;
        }
        store.finish(item.workspaceId(), item.id(), outcome);
    }
}
