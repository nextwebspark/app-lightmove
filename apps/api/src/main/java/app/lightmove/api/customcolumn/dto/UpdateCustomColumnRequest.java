package app.lightmove.api.customcolumn.dto;

import jakarta.validation.constraints.Size;

/**
 * A change to one column: rename it, change what it holds, or take it off the grid. Every field is
 * optional and a null leaves that half alone, the same shape
 * {@link app.lightmove.api.triagecompany.dto.UpdateTriageCompanyRequest} uses for the same reason —
 * hiding a column must not also have to restate its name.
 *
 * <p>There is no {@code fieldKey} here either. The key is immutable by design: every value already
 * stored points at it, and renaming is a change to the header and to nothing else.
 */
public record UpdateCustomColumnRequest(
        @Size(max = 60, message = "A column name must be 60 characters or fewer")
        String label,

        @Size(max = 16)
        String dataType,

        Boolean hidden
) {}
