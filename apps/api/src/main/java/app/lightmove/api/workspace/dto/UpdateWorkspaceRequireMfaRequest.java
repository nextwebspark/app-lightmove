package app.lightmove.api.workspace.dto;

import jakarta.validation.constraints.NotNull;

/** Settings → Security's requirement that every member holds a two-factor factor. */
public record UpdateWorkspaceRequireMfaRequest(
        @NotNull(message = "Choose whether two-factor authentication is required")
        Boolean required
) {}
