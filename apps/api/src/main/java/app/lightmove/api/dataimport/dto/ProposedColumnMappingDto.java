package app.lightmove.api.dataimport.dto;

/**
 * One column's mapping, in both directions. Read in order: {@code targetField}, else an existing
 * {@code customFieldKey}, else a new {@code customLabel}, else ignored.
 */
public record ProposedColumnMappingDto(
        int index,
        String header,
        String targetField,
        String customFieldKey,
        String customLabel,
        String customTarget,
        String customType
) {}
