package app.lightmove.api.core.config;

/** The mandate's role definition: the attached position description, reading one, and the role templates it is drafted from. */
public record PositionSettings(
        PositionDocumentSettings document,
        PositionExtractionSettings extraction,
        PositionTemplateSettings template
) {}
