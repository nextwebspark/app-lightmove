package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ProjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The creator becomes the project's lead; there is no lead to choose up front. {@code targetDate} is the
 * brief's hire date and the New position modal no longer sends it; the timeline is the three dates after it.
 */
public record CreateProjectRequest(
        @NotNull(message = "Choose a business unit")
        UUID clientId,

        @NotBlank(message = "Enter the position title")
        @Size(max = 160, message = "That title is too long")
        String positionTitle,

        LocalDate targetDate,

        ProjectType projectType,

        LocalDate startDate,

        LocalDate deliveryDate,

        LocalDate mappingTargetDate
) {
    /** A caller that names no type gets the column's default, so every pre-V70 client keeps working. */
    public CreateProjectRequest {
        projectType = projectType == null ? ProjectType.SEARCH : projectType;
    }
}
