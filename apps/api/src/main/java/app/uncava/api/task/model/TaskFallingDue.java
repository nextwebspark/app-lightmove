package app.uncava.api.task.model;

import java.time.Instant;
import java.util.UUID;

/** An open task about to fall due, or past it, claimed once by the due scan and published in its transaction. */
public record TaskFallingDue(UUID workspaceId, UUID projectId, UUID personId, UUID candidateId, UUID taskId,
                             UUID assigneeUserId, Instant dueAt, boolean overdue) {}
