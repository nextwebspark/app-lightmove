package app.lightmove.api.position.dto;

/**
 * One proposed field. {@code id} is a per-response sequence number, since a repeatable key recurs and
 * an array index shifts; {@code snippet} is null when unverified against the document.
 */
public record ProposedFieldDto(int id, String fieldKey, String value, String confidence, String snippet,
                               String origin) {}
