package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * A role-template JSON file imported in Settings — {@code lightmove.position.template-import.*}.
 *
 * <p>No content-type allowlist, unlike {@link PositionDocumentSettings}: the bytes are parsed as JSON
 * whatever the part claims, and anything that is not the template format is refused as unreadable.
 */
public record PositionTemplateImportSettings(
        /** Ceiling on one upload, in bytes. The file is held whole in memory while it is parsed. */
        @DefaultValue("1048576") long maxFileSizeBytes,

        /** Ceiling on templates in one file. Refused whole rather than truncated, like a spreadsheet's rows. */
        @DefaultValue("100") int maxTemplates
) {

    public PositionTemplateImportSettings {
        if (maxFileSizeBytes < 1) {
            throw new IllegalArgumentException(
                    "lightmove.position.template-import.max-file-size-bytes must be positive, but was "
                            + maxFileSizeBytes);
        }
        if (maxTemplates < 1) {
            throw new IllegalArgumentException(
                    "lightmove.position.template-import.max-templates must be positive, but was " + maxTemplates);
        }
    }
}
