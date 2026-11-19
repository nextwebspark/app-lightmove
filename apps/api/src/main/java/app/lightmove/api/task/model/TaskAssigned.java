package app.lightmove.api.task.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * A task was put on someone by someone else, published in the writing transaction. {@code noteId} is the note it
 * came from, or null.
 */
@NamedInterface(name = "api", propagate = false)
public record TaskAssigned(UUID workspaceId, UUID projectId, UUID personId, UUID candidateId, UUID taskId,
                           UUID noteId, UUID assigneeUserId, UUID actorUserId) {}
