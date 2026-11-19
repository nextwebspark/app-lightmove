package app.lightmove.api.task.model;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** An open task about to fall due, or past it, claimed once by the due scan and published in its transaction. */
@NamedInterface(name = "api", propagate = false)
public record TaskFallingDue(UUID workspaceId, UUID projectId, UUID personId, UUID candidateId, UUID taskId,
                             UUID assigneeUserId, Instant dueAt, boolean overdue) {}
