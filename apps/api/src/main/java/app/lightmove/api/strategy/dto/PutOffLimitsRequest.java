package app.lightmove.api.strategy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The off-limits list, replaced wholesale. Only identities travel: the display snapshot is resolved
 * from the universe on the server, and an id the universe does not hold is rejected rather than
 * stored as an unresolvable exclusion.
 */
public record PutOffLimitsRequest(
        @NotNull(message = "A company list is required, even if empty")
        @Size(max = 500, message = "Too many companies on the off-limits list")
        List<@NotNull @Size(max = 64) String> apolloAccountIds
) {}
