package app.uncava.api.workspace.model;

import java.util.UUID;

/**
 * A member's workspace roles changed or their membership ended: anything they held through a role they no longer
 * have — a position worked as an admin without a seat — is handed on in the same transaction.
 */
public record MemberAccessNarrowed(UUID workspaceId, UUID userId, UUID actorUserId) {}
