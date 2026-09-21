package app.lightmove.api.position.dto;

/**
 * One proposed field, as an extraction endpoint answers it. {@code id} is a per-response sequence
 * number — stable identity for the frontend to key rows on, since a repeatable field key (a
 * responsibility, a priority, a criterion, a competency) can appear more than once and array index
 * alone is not a safe key (it shifts when a row is removed). {@code confidence} carries a
 * {@code ProposalConfidence} wire token; {@code snippet} is null when none could be verified against
 * the source document. {@code origin} carries a {@code ProposalOrigin} wire token — always
 * {@code "document"} today, since template backfill was retired.
 */
public record ProposedFieldDto(int id, String fieldKey, String value, String confidence, String snippet,
                               String origin) {}
