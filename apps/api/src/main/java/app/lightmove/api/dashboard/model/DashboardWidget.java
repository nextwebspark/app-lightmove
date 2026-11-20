package app.lightmove.api.dashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * One chart on a dashboard as stored (V231): the library query it draws, how ({@code chart}, a {@code ChartType}
 * token), its options, an optional title and its place on the grid. Never a figure. A row saved before the grid
 * carries a {@code size} token and no layout; the screen flows those into place.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DashboardWidget(String queryId, String chart, Map<String, String> options, String title,
                              WidgetLayout layout) {

    public DashboardWidget {
        options = options == null ? Map.of() : Map.copyOf(options);
    }

    public DashboardWidget placedAt(WidgetLayout newLayout) {
        return new DashboardWidget(queryId, chart, options, title, newLayout);
    }
}
