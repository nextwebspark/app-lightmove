package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A mandate's status for someone, apart from {@link SaveCandidateRequest} so flicking the pill never resubmits
 * (and overwrites) a stale profile. A way out that offers reasons is given one, and a note where it asks for one.
 */
public record UpdateCandidateStatusRequest(
        @NotBlank(message = "A status is required")
        @Size(max = 40)
        String status,
        UUID reasonId,
        @Size(max = 500, message = "Keep the note to 500 characters")
        String note
) {}
