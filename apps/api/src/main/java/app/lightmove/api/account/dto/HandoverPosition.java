package app.lightmove.api.account.dto;

import java.util.UUID;

/** A live position the person is the only lead of. */
public record HandoverPosition(UUID workspaceId, String workspaceName, UUID projectId, String title) {}
