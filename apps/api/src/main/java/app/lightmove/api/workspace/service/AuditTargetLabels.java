package app.lightmove.api.workspace.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The names the audit log puts beside a position or a client its rows point at, implemented in {@code project} so the
 * dependency stays one-way. Read from the live rows, never from what an event's metadata recorded.
 */
public interface AuditTargetLabels {

    /** Archived positions included; an id of another workspace is absent from the map. */
    Map<UUID, String> positionTitles(UUID workspaceId, Collection<UUID> projectIds);

    /** Archived clients included; an id of another workspace is absent from the map. */
    Map<UUID, String> clientNames(UUID workspaceId, Collection<UUID> clientIds);
}
