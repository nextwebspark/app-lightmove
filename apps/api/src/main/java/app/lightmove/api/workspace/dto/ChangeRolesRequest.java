package app.lightmove.api.workspace.dto;

import app.lightmove.api.core.security.rbac.WorkspaceRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/** Replace-set over staff roles: the staff roles the member holds afterwards. A CLIENT role they hold is kept. */
public record ChangeRolesRequest(
        @NotEmpty(message = "Choose at least one role")
        Set<@NotNull(message = "Choose at least one role") WorkspaceRole> roles
) {}
