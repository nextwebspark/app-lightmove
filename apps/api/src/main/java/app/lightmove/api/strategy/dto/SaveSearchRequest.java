package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.constant.SearchVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The filter is read from the autosaved strategy, not the request. A missing visibility means SHARED. */
public record SaveSearchRequest(
        @NotBlank(message = "A name is required")
        @Size(max = 120, message = "A name must be 120 characters or fewer")
        String name,

        SearchVisibility visibility
) {
    public SaveSearchRequest {
        visibility = visibility == null ? SearchVisibility.SHARED : visibility;
    }
}
