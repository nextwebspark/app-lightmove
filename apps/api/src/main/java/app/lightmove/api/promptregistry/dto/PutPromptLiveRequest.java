package app.lightmove.api.promptregistry.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Which version serves from now, and why: recorded with the move. An earlier version is a rollback. The reason may
 * be left out only for a version an eval run on the held-back cases found no worse than the one serving.
 */
public record PutPromptLiveRequest(
        @NotNull @Positive Integer version,
        @Size(max = 500) String reason
) {
}
