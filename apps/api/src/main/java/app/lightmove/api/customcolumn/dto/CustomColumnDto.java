package app.lightmove.api.customcolumn.dto;

/**
 * One custom column as the grid needs it. {@code fieldKey} is exposed because the row's {@code customFields} map is
 * keyed by it, not by the id.
 */
public record CustomColumnDto(
        String id,
        String target,
        String fieldKey,
        String label,
        String dataType,
        int displayOrder,
        boolean hidden
) {}
