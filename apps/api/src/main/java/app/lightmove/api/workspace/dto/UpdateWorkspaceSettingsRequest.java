package app.lightmove.api.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Settings → General edits. A blank {@code apolloAccountId} files the typed name with no company. */
public record UpdateWorkspaceSettingsRequest(
        @NotBlank(message = "Enter the workspace name")
        @Size(max = 160, message = "That name is too long")
        String name,

        @Size(max = 64)
        String apolloAccountId,

        String defaultRegion,
        String defaultCurrency
) {}
