package app.lightmove.api.task.dto;

import app.lightmove.api.task.model.Task;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * A change to a task; every field optional. {@code status} {@code done} completes it and {@code open} reopens
 * it. A blank {@code detail} clears it.
 */
public record UpdateTaskRequest(
        @Size(max = Task.MAX_TITLE) @Pattern(regexp = Task.ONE_LINE, message = Task.ONE_LINE_ONLY)
        String title,
        String kind,
        UUID assigneeUserId,
        Instant dueAt,
        @Size(max = Task.MAX_DETAIL) String detail,
        String status
) {}
