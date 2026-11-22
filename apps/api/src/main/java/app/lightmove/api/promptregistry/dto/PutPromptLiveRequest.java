package app.lightmove.api.promptregistry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Which version serves from now, and why: recorded with the move. An earlier version is a rollback. */
public record PutPromptLiveRequest(
        @NotNull @Positive Integer version,
        @NotBlank @Size(max = 500) String reason
) {
}
