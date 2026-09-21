package app.lightmove.api.assistant.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Filing some of what the assistant proposed.
 *
 * <p>Refs rather than company identities, and that is the trust model rather than a convenience. The
 * fields a company lands with come from the stored proposal, which the server resolved; a request
 * carrying its own would be a door to filing a company under a name of the caller's choosing — the
 * thing {@code AddTriageCompanyRequest} already refuses to allow.
 *
 * <p>{@code status} is the stage they land at, because the card offers all three and the proposal
 * chose none. Omitted, they land in universe.
 */
public record AcceptProposalRequest(
        @NotEmpty(message = "Select at least one company")
        @Size(max = 200, message = "Too many companies in one request")
        List<@NotEmpty @Size(max = 64) String> refs,

        @Size(max = 32)
        String status
) {}
