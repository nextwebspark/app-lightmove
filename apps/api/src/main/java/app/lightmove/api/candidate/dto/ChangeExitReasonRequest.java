package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A different reason, or a different note, for someone already out of process. */
public record ChangeExitReasonRequest(
        @NotNull(message = "Choose why they left")
        UUID reasonId,
        @Size(max = 500, message = "Keep the note to 500 characters")
        String note
) {}
