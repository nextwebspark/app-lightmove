package app.lightmove.api.enrichment.importrun.model;

import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentKind;
import java.util.UUID;

/** A row the worker has claimed, with everything researching it needs. */
public record ClaimedEnrichmentItem(UUID id, UUID workspaceId, UUID projectId, UUID runId, UUID requestedBy,
                                    ImportEnrichmentKind kind, UUID targetId, String linkedinSlug) {}
