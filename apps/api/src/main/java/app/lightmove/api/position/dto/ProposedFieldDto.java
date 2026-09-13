package app.lightmove.api.position.dto;

/**
 * One proposed field, as the extraction endpoint answers it. {@code id} is a per-response sequence
 * number — stable identity for the frontend to key rows on, since two responsibility rows share the
 * same {@code fieldKey} and array index alone is not a safe key (it shifts when a row is removed).
 * {@code confidence} carries a {@code ProposalConfidence} wire token; {@code snippet} is null when
 * none could be verified against the source document. {@code origin} carries a {@code ProposalOrigin}
 * wire token — {@code "document"} or {@code "template"}.
 */
public record ProposedFieldDto(int id, String fieldKey, String value, String confidence, String snippet,
                               String origin) {}
