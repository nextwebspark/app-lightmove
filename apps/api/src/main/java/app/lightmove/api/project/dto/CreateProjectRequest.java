package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ProjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The creator becomes the project's lead; there is no lead to choose up front.
 *
 * <p>The milestones are checked together rather than field by field, in
 * {@code MandateTimeline.requested} — which date is required depends on the type, and an omitted
 * mapping target on a search is derived rather than refused. {@code targetDate} is the brief's target
 * start and is no milestone: the modal does not ask for it.
 */
public record CreateProjectRequest(
        @NotNull(message = "Choose a client")
        UUID clientId,

        @NotBlank(message = "Enter the position title")
        @Size(max = 160, message = "That title is too long")
        String positionTitle,

        ProjectType projectType,

        LocalDate startDate,

        LocalDate mappingTargetDate,

        LocalDate shortlistTargetDate,

        LocalDate targetDate
) {
    /** A mandate that states no type is the narrower engagement: it claims only what it can show. */
    public CreateProjectRequest {
        projectType = projectType == null ? ProjectType.MAPPING : projectType;
    }
}
