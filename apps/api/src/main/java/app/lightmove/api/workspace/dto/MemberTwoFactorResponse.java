package app.lightmove.api.workspace.dto;

import app.lightmove.api.core.security.rbac.WorkspaceRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One member's two-factor standing, as an admin of the workspace sees it.
 *
 * @param resettable whether this admin may reset it here: never their own, and never a member who is also in another
 *                   workspace, since the factor is the person's and a reset would reach there too
 */
public record MemberTwoFactorResponse(
        UUID memberId,
        UUID userId,
        String fullName,
        String email,
        String avatarUrl,
        List<WorkspaceRole> roles,
        boolean enabled,
        Instant enabledAt,
        boolean resettable
) {}
