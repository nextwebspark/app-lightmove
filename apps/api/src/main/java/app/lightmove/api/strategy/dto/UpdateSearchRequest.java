package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.constant.SearchVisibility;
import jakarta.validation.constraints.Size;

/**
 * Editing a saved search's label and tier. Its filter is not here: re-capturing the mandate's current
 * filter onto a search is a separate, explicit act, so that renaming one can never silently move the
 * scope it stands for.
 *
 * <p>Both fields are optional and absent means "leave this alone". They have to agree on that: a tier
 * toggle forced to resend a name it never touched writes back whatever its client last cached, which
 * on a shared search is how one person's rename silently reverts another's.
 */
public record UpdateSearchRequest(
        @Size(max = 120, message = "A name must be 120 characters or fewer")
        String name,

        SearchVisibility visibility
) {}
