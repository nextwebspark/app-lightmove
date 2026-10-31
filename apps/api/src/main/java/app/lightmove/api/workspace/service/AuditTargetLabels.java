package app.lightmove.api.workspace.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Position titles and client names for the audit log, implemented in {@code project} so the dependency stays one-way.
 * An id of another workspace is absent from the map.
 */
public interface AuditTargetLabels {

    Map<UUID, String> positionTitles(UUID workspaceId, Collection<UUID> projectIds);

    Map<UUID, String> clientNames(UUID workspaceId, Collection<UUID> clientIds);
}
