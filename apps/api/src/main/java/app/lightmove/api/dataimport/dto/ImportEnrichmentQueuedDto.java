package app.lightmove.api.dataimport.dto;

import app.lightmove.api.enrichment.importrun.model.QueuedEnrichment;
import java.util.UUID;

/** What an import handed to enrichment; the dialog follows {@code runId} until it is done. */
public record ImportEnrichmentQueuedDto(UUID runId, int peopleQueued, int companiesQueued) {

    public static ImportEnrichmentQueuedDto of(QueuedEnrichment queued) {
        return new ImportEnrichmentQueuedDto(queued.runId(), queued.peopleQueued(), queued.companiesQueued());
    }
}
