package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Only the identity travels: the snapshot is resolved server-side, so a client cannot file a company
 * under a name of its choosing. {@code status} is the landing stage, in universe when omitted.
 */
public record AddTriageCompanyRequest(
        @NotBlank(message = "A company is required")
        @Size(max = 64)
        String apolloAccountId,

        @Size(max = 32)
        String status,

        @Size(max = 2000, message = "A note must be 2000 characters or fewer")
        String note
) {}
