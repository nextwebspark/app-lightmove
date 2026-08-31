package app.lightmove.api.customcolumn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Adding a column to a mandate's grid by hand — the same act the import performs for an unmapped
 * spreadsheet header, through the same service.
 *
 * <p>No {@code fieldKey}: it is derived from the label server-side and is not the caller's to choose.
 * A client that picked its own key could aim a new column at another column's stored values.
 */
public record DefineCustomColumnRequest(
        @NotBlank
        @Size(max = 16)
        String target,

        @NotBlank(message = "Give the column a name")
        @Size(max = 60, message = "A column name must be 60 characters or fewer")
        String label,

        @Size(max = 16)
        String dataType
) {}
