package app.lightmove.api.workspace.dto;

import app.lightmove.api.core.tenant.WorkspaceMode;
import app.lightmove.api.workspace.model.CreateWorkspaceCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Signup step 3, and Settings' New workspace. {@code apolloAccountId} names the universe company picked, or is null for
 * a typed name. {@code dpaAccepted} is read only when founding: correcting the step never asks again.
 */
public record CreateWorkspaceRequest(
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
