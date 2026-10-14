package app.lightmove.api.task.dto;

import app.lightmove.api.task.model.Task;
import app.lightmove.api.task.model.TaskPreset;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A change to a preset; every field optional. {@code clearDueOffset} drops the default due date. */
public record UpdateTaskPresetRequest(
        @Size(max = TaskPreset.MAX_LABEL) @Pattern(regexp = Task.ONE_LINE, message = Task.ONE_LINE_ONLY)
        String label,
        String kind,
        @Min(0) @Max(TaskPreset.MAX_DUE_OFFSET_DAYS) Integer dueOffsetDays,
        Boolean clearDueOffset,
        Boolean retired
) {}
