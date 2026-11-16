package app.lightmove.api.enrichment.importrun.model;

import java.util.UUID;

/** What one import handed the worker: the run the dialog follows, and how many of each it queued. */
public record QueuedEnrichment(UUID runId, int peopleQueued, int companiesQueued) {}
