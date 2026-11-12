package app.lightmove.api.workspace.model;

import java.util.UUID;

/** The workspace's settled zone moved; whatever copies a member's clock reads it again in the same transaction. */
public record WorkspaceDateTimeChanged(UUID workspaceId) {}
