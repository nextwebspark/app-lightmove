package app.lightmove.api.project.dto;

import app.lightmove.api.core.security.rbac.ProjectRole;
import jakarta.validation.constraints.NotNull;

/**
 * PUT of a seat: the one staff role the member holds on this mandate afterwards. Singular by
 * contract, so the one-role-per-seat rule cannot be forgotten in a runtime check. CLIENT is not
 * seatable here — it comes from attaching a representative — and a seat that already holds it
 * keeps it.
 */
public record PutTeamMemberRequest(
        @NotNull(message = "Choose a role")
        ProjectRole role
) {}
