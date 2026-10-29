package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The Companies stage one executive moves to: {@code inUniverse} or {@code declined}; Shortlisted is retired. */
public record UpdateCandidateTriageStatusRequest(
        @NotBlank(message = "A stage is required")
        @Size(max = 16)
        String status
) {}
