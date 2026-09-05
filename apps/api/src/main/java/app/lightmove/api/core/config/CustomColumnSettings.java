package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The ceiling on a mandate's custom grid columns — {@code lightmove.custom-column.*}.
 *
 * <p>A limit rather than none, because the columns are defined by whatever a spreadsheet's header row
 * happens to say. One mis-mapped import of a very wide file would otherwise define a column per header
 * and leave a mandate with a grid nobody can read across, which is tedious to undo one column at a
 * time. Counted across both grids, since it is a limit on one screen's width.
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
