package app.uncava.api.workspace.dto;

import app.uncava.api.core.security.rbac.WorkspaceRole;
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
