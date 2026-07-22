package app.lightmove.api.workspace.model;

import java.util.UUID;

/**
 * A client representative accepted their portal invitation. Published by {@code InvitationService} when
 * a CLIENT invitation is redeemed, and consumed by the project feature to flip the representative row to
 * ACTIVE.
 *
 * <p>Lives here, on the publisher's side, so the dependency runs project → workspace (the sanctioned
 * seam), never the reverse: workspace mints the membership and announces it in primitives, knowing
 * nothing of the {@code ClientRepresentative} the project feature keeps.
 */
public record ClientRepresentativeAcceptedEvent(UUID workspaceId, UUID clientId, String email, UUID userId) {}
