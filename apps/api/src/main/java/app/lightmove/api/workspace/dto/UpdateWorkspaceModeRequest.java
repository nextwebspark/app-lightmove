package app.lightmove.api.workspace.dto;

import app.lightmove.api.workspace.constant.WorkspaceMode;
import jakarta.validation.constraints.NotNull;

/** Settings → General's workspace type. */
public record UpdateWorkspaceModeRequest(
        @NotNull(message = "Say whether you hire for clients or for your own business")
        WorkspaceMode mode
) {}
