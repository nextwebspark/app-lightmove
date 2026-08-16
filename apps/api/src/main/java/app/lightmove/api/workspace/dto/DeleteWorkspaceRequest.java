package app.lightmove.api.workspace.dto;

import jakarta.validation.constraints.NotBlank;

/** Deletion demands the workspace name typed back; the server verifies it too. */
public record DeleteWorkspaceRequest(
        @NotBlank(message = "Type the workspace name to confirm")
        String confirmName
) {}
