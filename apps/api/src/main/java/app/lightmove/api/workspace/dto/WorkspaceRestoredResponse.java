package app.lightmove.api.workspace.dto;

import java.util.UUID;

public record WorkspaceRestoredResponse(UUID workspaceId, int membersRestored) {
}
