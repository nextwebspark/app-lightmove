package app.lightmove.api.customcolumn.dto;

import jakarta.validation.constraints.Size;

/**
 * A null leaves that half alone, as in {@link app.lightmove.api.triagecompany.dto.UpdateTriageCompanyRequest}.
 * No {@code fieldKey}: it is immutable, since every stored value points at it.
 */
public record UpdateCustomColumnRequest(
        @Size(max = 60, message = "A column name must be 60 characters or fewer")
        String label,

        @Size(max = 16)
        String dataType,

        Boolean hidden
) {}
