package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A mandate's status for someone, apart from {@link SaveCandidateRequest} so flicking the pill never resubmits
 * (and overwrites) a stale profile.
 */
public record UpdateCandidateStatusRequest(
        @NotBlank(message = "A status is required")
        @Size(max = 40)
        String status
) {}
