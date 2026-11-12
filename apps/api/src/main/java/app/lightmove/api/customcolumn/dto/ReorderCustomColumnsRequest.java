package app.lightmove.api.customcolumn.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A grid's custom columns' new order as the whole id list (a drag moves every later column, and a whole list cannot be
 * half-applied). Ids the project does not own are refused, not skipped.
 */
public record ReorderCustomColumnsRequest(
        @NotEmpty
        @Size(max = 100)
        List<String> columnIds
) {}
