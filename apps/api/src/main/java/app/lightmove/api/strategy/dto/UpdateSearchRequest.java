package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.constant.SearchVisibility;
import jakarta.validation.constraints.Size;

/**
 * Both fields optional, absent meaning "leave alone": a tier toggle forced to resend a cached name
 * silently reverted a teammate's rename. Re-capturing the filter is a separate act.
 */
public record UpdateSearchRequest(
        @Size(max = 120, message = "A name must be 120 characters or fewer")
        String name,

        SearchVisibility visibility
) {}
