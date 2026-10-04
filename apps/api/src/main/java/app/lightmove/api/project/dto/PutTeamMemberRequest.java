package app.lightmove.api.project.dto;

import app.lightmove.api.core.security.rbac.ProjectRole;
import jakarta.validation.constraints.NotNull;

/**
 * The one staff role the seat holds afterwards — singular by contract, so one-role-per-seat cannot be
 * forgotten. CLIENT comes only from attaching a representative.
 */
public record PutTeamMemberRequest(
        @NotNull(message = "Choose a role")
        ProjectRole role
) {}
