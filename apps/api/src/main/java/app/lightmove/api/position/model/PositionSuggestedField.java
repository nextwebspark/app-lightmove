package app.lightmove.api.position.model;

/** One field a document reading filled (V244); {@code documentItems} is a list's row count, null for a scalar. */
public record PositionSuggestedField(String fieldKey, String promptId, int version, Integer documentItems) {
}
