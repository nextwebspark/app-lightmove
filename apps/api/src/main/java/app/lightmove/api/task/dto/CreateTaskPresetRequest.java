package app.lightmove.api.task.dto;

import app.lightmove.api.task.model.TaskPreset;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new preset; {@code dueOffsetDays} null leaves the due date to the person. */
public record CreateTaskPresetRequest(
        @NotBlank @Size(max = TaskPreset.MAX_LABEL) String label,
        @NotBlank String kind,
        @Min(0) @Max(TaskPreset.MAX_DUE_OFFSET_DAYS) Integer dueOffsetDays
) {}
