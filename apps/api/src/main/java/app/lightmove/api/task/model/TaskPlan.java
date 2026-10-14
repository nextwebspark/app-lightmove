package app.lightmove.api.task.model;

import app.lightmove.api.task.constant.TaskKind;
import java.time.Instant;
import java.util.UUID;

/** Everything a new task is filed with, whichever door files it. */
public record TaskPlan(
        UUID workspaceId,
        UUID projectId,
        UUID personId,
        UUID candidateId,
        UUID noteId,
        UUID presetId,
        String title,
        TaskKind kind,
        String detail,
        UUID assigneeUserId,
        Instant dueAt
) {}
