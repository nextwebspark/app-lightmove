package app.lightmove.api.workspace.dto;

import app.lightmove.api.core.security.rbac.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

/** An outstanding invitation, as the Settings → Members screen lists them. */
public record InvitationResponse(
        UUID id,
        String email,
        WorkspaceRole role,
        String invitedByName,
        Instant createdAt,
        Instant expiresAt
) {}
