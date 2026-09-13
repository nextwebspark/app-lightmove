package app.lightmove.api.core.config;

/** The position brief's own tunables: the attached position description, and the role-template import. */
public record PositionSettings(PositionDocumentSettings document, PositionTemplateImportSettings templateImport) {}
