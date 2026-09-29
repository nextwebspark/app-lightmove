package app.lightmove.api.enrichment.sourcing.model;

import java.util.UUID;

/** Starts a run once the request that created it commits; the worker reads the row as it then stands. */
public record ExecutiveSourcingRequested(UUID runId, UUID projectId, UUID workspaceId, UUID requestedBy) {}
