package app.lightmove.api.workspace.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * A member's workspace roles changed or their membership ended: anything they held through a role they no longer
 * have — a position worked as an admin without a seat — is handed on in the same transaction.
 */
@NamedInterface(name = "api", propagate = false)
public record MemberAccessNarrowed(UUID workspaceId, UUID userId, UUID actorUserId) {}
