package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A job brief and a candidate profile, both as plain text, to compare for fit. */
public record ShortlistRequest(
        @NotBlank(message = "Give the job brief")
        @Size(max = 20_000, message = "That job brief is too long")
        String jobBrief,

        @NotBlank(message = "Give the candidate profile")
        @Size(max = 20_000, message = "That candidate profile is too long")
        String candidateProfile
) {}
