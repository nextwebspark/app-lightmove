package app.lightmove.api.dataimport.dto;

import java.util.List;

/** The mapping step's input. Nothing is written or held server-side; confirming re-posts the file. */
public record ImportPreviewResponse(
        String fileName,
        int rowCount,
        List<ImportColumnDto> columns,
        List<ImportTargetFieldDto> availableFields,
        /** A {@code MappingSource} wire token. */
        String mappingSource
) {}
