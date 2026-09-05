package app.lightmove.api.dataimport.dto;

import java.util.List;

/**
 * What the import did. Counts rather than the rows themselves: the grid behind the dialog reloads
 * from its own endpoints, and echoing several thousand imported rows back would be a second copy of
 * the file to no purpose.
 *
 * <p>{@code companiesSkipped} is its own number rather than folded into the errors, because it counts
 * something that went right — a company taken from the Apollo universe keeps the export's own facts,
 * so an import fills in its custom columns and leaves its snapshot alone. A user seeing "12 updated"
 * for a file where twelve names matched market rows would reasonably expect their headcounts to have
 * changed.
 */
public record ImportSummaryResponse(
        int rowsRead,
        int companiesCreated,
        int companiesUpdated,
        int companiesSkipped,
        int candidatesCreated,
        int candidatesUpdated,
        List<String> customColumnsCreated,
        List<ImportRowErrorDto> rowErrors
) {}
