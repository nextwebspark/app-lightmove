package app.lightmove.api.workspace.dto;

import app.lightmove.api.core.security.rbac.WorkspaceRole;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

/** Replace-set: the full set of roles the member holds afterwards. */
public record ChangeRolesRequest(
        @NotEmpty(message = "Choose at least one role")
        Set<WorkspaceRole> roles
) {}
