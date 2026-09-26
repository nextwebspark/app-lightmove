package app.lightmove.api.workspace.dto;

import app.lightmove.api.workspace.model.CreateWorkspaceCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Signup step 3. {@code apolloAccountId} names the universe company picked, or is null for a typed name. */
public record CreateWorkspaceRequest(
        @NotBlank(message = "Enter your organization's name")
        @Size(max = 160, message = "That name is too long")
        String name,

        @Size(max = 64)
        String apolloAccountId,

        String companySize,
        String primaryRegion,
        String teamFocus
) {

    public CreateWorkspaceCommand toCommand() {
        return new CreateWorkspaceCommand(name, apolloAccountId, companySize, primaryRegion, teamFocus);
    }
}
