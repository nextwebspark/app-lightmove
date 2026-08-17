package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** The creator becomes the project's lead; there is no lead to choose up front. */
public record CreateProjectRequest(
        @NotNull(message = "Choose a client")
        UUID clientId,

        @NotBlank(message = "Enter the position title")
        @Size(max = 160, message = "That title is too long")
        String positionTitle,

        LocalDate targetDate
) {}
