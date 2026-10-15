package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Archiving demands the record's name typed back; the server checks it too. */
public record ArchiveClientRequest(
        @NotBlank(message = "Type the name to confirm")
        @Size(max = 200, message = "That is longer than any name")
        String confirmName
) {}
