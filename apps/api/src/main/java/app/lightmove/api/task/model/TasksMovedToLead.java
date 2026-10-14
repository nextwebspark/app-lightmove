package app.lightmove.api.task.model;

import java.util.UUID;

/** A teammate left a position and their open tasks there went to its lead, published in that transaction. */
public record TasksMovedToLead(UUID workspaceId, UUID projectId, UUID leadUserId, UUID fromUserId, int count) {}
