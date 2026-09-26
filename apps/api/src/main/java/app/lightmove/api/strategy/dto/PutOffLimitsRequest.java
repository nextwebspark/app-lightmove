package app.lightmove.api.strategy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Replaced wholesale. Only ids travel; one the universe does not hold is rejected. */
public record PutOffLimitsRequest(
        @NotNull(message = "A company list is required, even if empty")
        @Size(max = 500, message = "Too many companies on the off-limits list")
        List<@NotNull @Size(max = 64) String> apolloAccountIds
) {}
