package app.lightmove.api.customcolumn.dto;

/**
 * One custom column as the grid needs it: what to key values by, what to put in the header, what kind
 * of cell to render, and whether to render it at all.
 *
 * <p>{@code fieldKey} is exposed rather than hidden behind the id, because it is the key the row's
 * {@code customFields} map is addressed by — a client holding only the id could not read a value out
 * of the row it just fetched.
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
