package app.lightmove.api.position.dto;

/**
 * One proposed field, as the extraction endpoint answers it. {@code confidence} carries a {@code
 * ProposalConfidence} wire token; {@code snippet} is null when none could be verified against the
 * source document.
 */
public record ProposedFieldDto(String fieldKey, String value, String confidence, String snippet) {}
