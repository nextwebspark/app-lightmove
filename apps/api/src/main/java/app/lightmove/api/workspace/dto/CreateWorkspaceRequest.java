package app.lightmove.api.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Signup step 2. */
public record CreateWorkspaceRequest(
        @NotBlank(message = "Enter your organization's name")
        @Size(max = 160, message = "That name is too long")
        String name,

        String companySize,
        String primaryRegion,
        String teamFocus
) {}
