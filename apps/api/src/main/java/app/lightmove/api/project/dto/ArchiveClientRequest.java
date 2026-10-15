package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotBlank;

/** Archiving demands the record's name typed back; the server checks it too. */
public record ArchiveClientRequest(
        @NotBlank(message = "Type the name to confirm")
        String confirmName
) {}
