package app.uncava.api.task.model;

import java.util.UUID;

/** Someone holding open tasks in a workspace. */
public record TaskHolder(UUID workspaceId, UUID userId) {}
