package app.lightmove.api.dataimport.dto;

import java.util.List;

/**
 * What the import did — counts rather than the rows themselves, since the grid behind the dialog
 * reloads from its own endpoints.
 *
 * <p>{@code companiesSkipped} is its own number rather than folded into the errors, because it counts
 * something that went right: a company taken from the Apollo universe keeps the export's own facts,
 * so an import fills in its custom columns and leaves its snapshot alone.
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
