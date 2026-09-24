package app.lightmove.api.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Settings → General edits. {@code apolloAccountId} is required so no caller clears a picked firm by
 * leaving it out: an empty string is the explicit "typed name, no company", which clears the snapshot.
 */
public record UpdateWorkspaceSettingsRequest(
        @NotBlank(message = "Enter the workspace name")
        @Size(max = 160, message = "That name is too long")
        String name,

        @NotNull(message = "Say which company, or none")
        @Size(max = 64)
        String apolloAccountId,

        String defaultRegion,
        String defaultCurrency
) {}
