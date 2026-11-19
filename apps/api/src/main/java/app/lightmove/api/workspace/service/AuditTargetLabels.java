package app.lightmove.api.workspace.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * Position titles and client names for the audit log, implemented in {@code project} so the dependency stays one-way.
 * An id of another workspace is absent from the map.
 */
@NamedInterface(name = "api", propagate = false)
public interface AuditTargetLabels {

    Map<UUID, String> positionTitles(UUID workspaceId, Collection<UUID> projectIds);

    Map<UUID, String> clientNames(UUID workspaceId, Collection<UUID> clientIds);
}
