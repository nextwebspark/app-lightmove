package app.lightmove.api.task.dto;

import app.lightmove.api.task.model.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** A new task. A preset is a convenience: the title is whatever the person settled on, typed or picked. */
public record CreateTaskRequest(
        @NotBlank @Size(max = Task.MAX_TITLE) @Pattern(regexp = Task.ONE_LINE, message = Task.ONE_LINE_ONLY)
        String title,
        @NotBlank String kind,
        @NotNull UUID assigneeUserId,
        @NotNull Instant dueAt,
        @Size(max = Task.MAX_DETAIL) String detail,
        UUID presetId,
        UUID noteId
) {}
