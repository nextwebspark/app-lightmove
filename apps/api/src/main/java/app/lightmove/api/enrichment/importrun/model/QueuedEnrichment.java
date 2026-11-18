package app.lightmove.api.enrichment.importrun.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** What one import handed the worker: the run the dialog follows, and how many of each it queued. */
@NamedInterface(name = "api", propagate = false)
public record QueuedEnrichment(UUID runId, int peopleQueued, int companiesQueued) {}
