package app.lightmove.api.core.config;

/** The position brief's own tunables: the attached position description, and reading one. */
public record PositionSettings(PositionDocumentSettings document, PositionExtractionSettings extraction) {}
