package app.lightmove.api.workspace.model;

import java.util.UUID;

/**
 * A CLIENT invitation was redeemed; {@code project} activates the representative row. Lives on the
 * publisher's side so the dependency runs project → workspace, never the reverse.
 */
public record ClientRepresentativeAcceptedEvent(UUID workspaceId, UUID clientId, String email, UUID userId) {}
