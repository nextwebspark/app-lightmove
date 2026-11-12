package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code lightmove.position.template.*}: the template JSON import's ceilings (not {@code …import.*}, a Java keyword).
 * No content-type allowlist: the bytes are parsed as JSON whatever the part claims.
 */
public record PositionTemplateSettings(
        /** Ceiling on one import, in bytes. The file is held whole in memory while it is parsed. */
        @DefaultValue("1048576") long maxImportFileSizeBytes,

        /** Ceiling on templates in one import. Refused whole rather than truncated, like a spreadsheet's rows. */
        @DefaultValue("100") int maxImportTemplates
) {

    public PositionTemplateSettings {
        if (maxImportFileSizeBytes < 1) {
            throw new IllegalArgumentException(
                    "lightmove.position.template.max-import-file-size-bytes must be positive, but was "
                            + maxImportFileSizeBytes);
        }
        if (maxImportTemplates < 1) {
            throw new IllegalArgumentException(
                    "lightmove.position.template.max-import-templates must be positive, but was "
                            + maxImportTemplates);
        }
    }
}
