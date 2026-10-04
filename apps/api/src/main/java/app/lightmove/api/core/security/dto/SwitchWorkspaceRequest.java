package app.lightmove.api.core.security.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** The workspace to move the session into. Must be one the caller is an active member of. */
public record SwitchWorkspaceRequest(@NotNull UUID workspaceId) {}
