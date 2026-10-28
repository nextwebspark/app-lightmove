package app.lightmove.api.candidate.dto;

/**
 * One field a save changed, as it read before and after; {@code from} or {@code to} is null where the field was
 * empty, and both are null where only that something changed is kept (a career history edited in place).
 */
public record FieldChangeResponse(String field, String from, String to) {}
