package app.lightmove.api.dataimport.dto;

import java.util.List;

/**
 * What the mapping step renders: the file as the server read it, the mapping it proposes, and the
 * vocabulary the dropdowns are built from.
 *
 * <p>Nothing has been written when this is returned, and no import is held open server-side. The
 * browser still has the file, so confirming re-posts it with the corrected mapping — which is why
 * there is no import id here, no staging table behind it, and no expiry to explain to anyone.
 */
public record ImportPreviewResponse(
        String fileName,
        int rowCount,
        List<ImportColumnDto> columns,
        List<ImportTargetFieldDto> availableFields,
        boolean mappedByModel
) {}
