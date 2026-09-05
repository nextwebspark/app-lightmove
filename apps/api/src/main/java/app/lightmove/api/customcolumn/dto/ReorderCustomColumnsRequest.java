package app.lightmove.api.customcolumn.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The new left-to-right order of a grid's custom columns, as the ids they should end up in.
 *
 * <p>Whole-list rather than a move-one-column verb: a drag changes every position after the one that
 * moved, and sending the result is both simpler to apply and impossible to leave half-done. Ids the
 * project does not own are refused rather than skipped — a partial reorder is a reorder the user did
 * not ask for.
 */
public record ReorderCustomColumnsRequest(
        @NotEmpty
        @Size(max = 100)
        List<String> columnIds
) {}
