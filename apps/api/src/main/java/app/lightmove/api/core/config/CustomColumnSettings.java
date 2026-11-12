package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The ceiling on a mandate's custom columns ({@code lightmove.custom-column.*}), counted across both grids, since
 * one mis-mapped wide import would otherwise define a column per header.
 */
public record CustomColumnSettings(
        @DefaultValue("40") int maxPerProject
) {

    public CustomColumnSettings {
        if (maxPerProject < 1) {
            throw new IllegalArgumentException(
                    "lightmove.custom-column.max-per-project must be positive, but was " + maxPerProject);
        }
    }
}
