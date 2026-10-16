package app.uncava.api.project.model;

import java.util.UUID;

/** Someone joined a position's team as staff, published in the seating transaction. Never a client seat. */
public record ProjectSeatTaken(UUID workspaceId, UUID projectId, UUID userId, UUID actorUserId) {}
