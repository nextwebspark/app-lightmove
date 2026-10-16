package app.uncava.api.task.dto;

import app.uncava.api.task.model.Task;
import app.uncava.api.task.model.TaskPreset;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A new preset; {@code dueOffsetDays} null leaves the due date to the person. */
public record CreateTaskPresetRequest(
        @NotBlank @Size(max = TaskPreset.MAX_LABEL) @Pattern(regexp = Task.ONE_LINE, message = Task.ONE_LINE_ONLY)
        String label,
        @NotBlank String kind,
        @Min(0) @Max(TaskPreset.MAX_DUE_OFFSET_DAYS) Integer dueOffsetDays
) {}
