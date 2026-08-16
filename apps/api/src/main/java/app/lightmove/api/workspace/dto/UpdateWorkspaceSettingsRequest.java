package app.lightmove.api.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Settings → General edits. */
public record UpdateWorkspaceSettingsRequest(
        @NotBlank(message = "Enter the workspace name")
        @Size(max = 160, message = "That name is too long")
        String name,

        String defaultRegion,
        String defaultCurrency
) {}
