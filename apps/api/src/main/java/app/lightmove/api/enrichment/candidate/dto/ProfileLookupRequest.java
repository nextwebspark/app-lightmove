package app.lightmove.api.enrichment.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A body rather than a query parameter, so the profile address stays out of request logs. */
public record ProfileLookupRequest(
        @NotBlank(message = "Paste a LinkedIn profile URL")
        @Size(max = 500)
        String linkedinUrl
) {}
