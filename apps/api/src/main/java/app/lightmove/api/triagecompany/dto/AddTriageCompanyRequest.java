package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Taking one company into the mandate's universe. Only the identity travels — the snapshot is
 * resolved from the universe server-side, so a client cannot file a company under a name of its own
 * choosing.
 *
 * <p>{@code status} is the landing stage: a company added while the consultant is looking at the
 * shortlist means a shortlisted company. Omitted, it lands in universe. {@code note} may travel
 * because it is the mandate's own remark rather than anything the market export resolves.
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
