package app.lightmove.api.project.dto;

import app.lightmove.api.core.security.rbac.ProjectRole;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import java.util.List;
import java.util.UUID;

/**
 * A seat on the team. {@code projectRoles} stays a list although a seat holds one staff role: a
 * client representative who also staffs the mandate holds {@code [CLIENT, LEAD]}.
 */
public record TeamMemberResponse(
        UUID memberId,
        UUID userId,
        String fullName,
        String avatarUrl,
        List<WorkspaceRole> workspaceRoles,
        List<ProjectRole> projectRoles
) {}
