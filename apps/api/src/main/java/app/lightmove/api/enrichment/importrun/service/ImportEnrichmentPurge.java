package app.lightmove.api.enrichment.importrun.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.tenant.EveryWorkspace;
import app.lightmove.api.core.tenant.TenantResolvers;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes finished enrichment rows past their keep-for, daily and apart from the drain: the drain walks only
 * workspaces with work queued, so one that never imports again would keep its rows for good.
 */
@Component
public class ImportEnrichmentPurge {

    private final ImportEnrichmentItemStore store;
    private final TenantResolvers resolvers;
    private final EveryWorkspace workspaces;
    private final Clock clock;
    private final Duration keepFor;

    ImportEnrichmentPurge(ImportEnrichmentItemStore store, TenantResolvers resolvers, EveryWorkspace workspaces,
                          Clock clock, LightMoveProperties properties) {
        this.store = store;
        this.resolvers = resolvers;
        this.workspaces = workspaces;
        this.clock = clock;
        this.keepFor = properties.spreadsheetImport().enrichmentKeepFor();
    }

    @Scheduled(cron = "${lightmove.spreadsheet-import.enrichment-purge}", zone = "UTC")
    public int purge() {
        Instant finishedBefore = clock.instant().minus(keepFor);
        return workspaces.sum(resolvers.workspacesWithImportEnrichmentExpired(finishedBefore),
                workspaceId -> store.purgeFinishedBefore(workspaceId, finishedBefore));
    }
}
