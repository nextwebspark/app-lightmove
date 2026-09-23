package app.lightmove.api.assistant.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The ticked companies of a card and the stage to file them at (omitted: in universe). Only ids the
 * stored card holds are accepted, so a request cannot file a company the assistant never proposed.
 */
public record AcceptProposalRequest(
        @NotEmpty(message = "Select at least one company")
        @Size(max = 200, message = "Too many companies in one request")
        List<@NotEmpty @Size(max = 64) String> apolloAccountIds,

        @Size(max = 32)
        String status
) {}
