package app.lightmove.api.task.model;

import java.util.UUID;

/** A task was put on someone by someone else, published in the writing transaction. */
public record TaskAssigned(UUID workspaceId, UUID projectId, UUID personId, UUID candidateId, UUID taskId,
                           UUID assigneeUserId, UUID actorUserId) {}
