package app.lightmove.api.dashboard.model;

import java.util.UUID;

/**
 * Who asks and about what: the workspace always from the principal, the project only on a position's dashboard
 * (null on the workspace's), already checked to be the caller's to read.
 */
public record ChartQueryContext(UUID userId, UUID workspaceId, UUID projectId) {}
