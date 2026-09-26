package app.lightmove.api.dataimport.dto;

import java.util.List;

/** Counts only. {@code companiesSkipped} is a success: a market company keeps the export's facts. */
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
