package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Taking one company into the mandate's universe. Only the identity travels — the snapshot is
 * resolved from the universe server-side, so a client cannot file a company under a name of its own
 * choosing.
 */
public record AddTriageCompanyRequest(
        @NotBlank(message = "A company is required")
        @Size(max = 64)
        String apolloAccountId
) {}
