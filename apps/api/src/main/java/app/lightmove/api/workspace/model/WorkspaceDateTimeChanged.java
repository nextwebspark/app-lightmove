package app.lightmove.api.workspace.model;

import java.util.UUID;

/**
 * The workspace's settled time zone or formats may have moved — an admin chose them, or the firm's country changed
 * under an unchosen zone. Whatever keeps a copy of a member's clock reads it again in the same transaction.
 */
public record WorkspaceDateTimeChanged(UUID workspaceId) {}
