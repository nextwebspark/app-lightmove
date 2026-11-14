package app.lightmove.api.workspace.dto;

import app.lightmove.api.workspace.constant.WorkspaceMode;
import app.lightmove.api.workspace.model.CreateWorkspaceCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Founding a workspace, at signup or from Settings. Only creating asks for the data processing agreement, and only
 * where the deployment names one; editing the organisation step afterwards is {@link CreateWorkspaceRequest}.
 */
public record FoundWorkspaceRequest(
        @NotBlank(message = "Enter your organization's name")
        @Size(max = 160, message = "That name is too long")
        String name,

        @NotNull(message = "Say whether you hire for clients or for your own business")
        WorkspaceMode mode,

        @Size(max = 64)
        String apolloAccountId,

        /** Absent reads as not accepted: most deployments name no agreement, and their clients never send it. */
        Boolean dpaAccepted
) {

    public boolean acceptsDpa() {
        return Boolean.TRUE.equals(dpaAccepted);
    }

    public CreateWorkspaceCommand toCommand() {
        return new CreateWorkspaceCommand(name, mode, apolloAccountId);
    }
}
