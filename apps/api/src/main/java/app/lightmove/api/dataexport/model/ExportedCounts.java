package app.lightmove.api.dataexport.model;

/** What a whole-workspace export carried, for its card. */
public record ExportedCounts(int people, int documents, int positions) {
}
