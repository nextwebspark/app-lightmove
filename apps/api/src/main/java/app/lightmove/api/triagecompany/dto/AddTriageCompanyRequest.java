package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Taking one company into the mandate's universe. Only the identity travels — the snapshot is
 * resolved from the universe server-side, so a client cannot file a company under a name of its own
 * choosing.
 *
 * <p>{@code status} is the landing stage, carried for the same reason {@link CaptureCompanyRequest}
 * carries one: a company added while the consultant is looking at the shortlist means a shortlisted
 * company, and bouncing it to the universe would ignore what the screen was told. Omitted, it lands
 * in universe.
 *
 * <p>{@code note} is the mandate's own first remark on the company. Every other field here belongs to
 * the market export and is resolved from it; this one never is, which is why it may travel.
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
