package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotBlank;

/** Archiving demands the position's title typed back; the server checks it too. */
public record ArchiveProjectRequest(
        @NotBlank(message = "Type the position title to confirm")
        String confirmName
) {}
