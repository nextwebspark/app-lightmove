package app.lightmove.api.dashboard.dto;

import app.lightmove.api.dashboard.model.HomeTile;
import app.lightmove.api.dashboard.model.WidgetLayout;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * One tile on Home: {@code kind} NEEDS, TODAY, POSITIONS or CHART, and its place on the grid. A chart tile names a
 * workspace library query as a dashboard widget does; a section's chart fields are ignored.
 */
public record HomeTileDto(
        @NotBlank @Size(max = 16) String kind,
        @NotNull WidgetLayout layout,
        @Size(max = 64) String queryId,
        @Size(max = 16) String chart,
        @Size(max = 8) Map<String, String> options,
        @Size(max = 80) String title
) {

    public static HomeTileDto from(HomeTile tile) {
        if (!tile.kind().isChart()) {
            return new HomeTileDto(tile.kind().value(), tile.layout(), null, null, null, null);
        }
        return new HomeTileDto(tile.kind().value(), tile.layout(), tile.queryId(), tile.chart(), tile.options(),
                tile.title());
    }
}
