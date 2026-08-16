package app.lightmove.api.workspace.dto;

import app.lightmove.api.core.security.rbac.WorkspaceRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One row of the active roster. */
public record MemberResponse(
        UUID memberId,
        UUID userId,
        String fullName,
        String email,
        String title,
        String avatarUrl,
        List<WorkspaceRole> roles,
        Instant joinedAt
) {}
