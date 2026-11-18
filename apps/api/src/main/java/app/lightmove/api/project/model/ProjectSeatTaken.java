package app.lightmove.api.project.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** Someone joined a position's team as staff, published in the seating transaction. Never a client seat. */
@NamedInterface(name = "api", propagate = false)
public record ProjectSeatTaken(UUID workspaceId, UUID projectId, UUID userId, UUID actorUserId) {}
