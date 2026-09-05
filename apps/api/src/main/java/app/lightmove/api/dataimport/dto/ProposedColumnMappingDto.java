package app.lightmove.api.dataimport.dto;

/**
 * What the server proposes a column becomes, and what the client sends back once a person has
 * confirmed or corrected it — one shape for both directions, so the mapping step has nothing to
 * translate.
 *
 * <p>Exactly one outcome applies per column, read in this order: a {@code targetField} maps onto a
 * built-in field; otherwise a {@code customFieldKey} fills a custom column the mandate already has;
 * otherwise a {@code customLabel} defines a new one; otherwise the column is ignored.
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
