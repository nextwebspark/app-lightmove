package app.uncava.api.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Archiving demands the position's title typed back; the server checks it too. */
public record ArchiveProjectRequest(
        @NotBlank(message = "Type the position title to confirm")
        @Size(max = 200, message = "That is longer than any position title")
        String confirmName
) {}
