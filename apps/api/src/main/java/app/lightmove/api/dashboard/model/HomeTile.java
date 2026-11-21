package app.lightmove.api.dashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * One tile on a member's Home as stored (V233): a section ({@code NEEDS}, {@code TODAY}, {@code POSITIONS}) or a
 * {@code CHART}, which also carries the library query, its chart type, options and title as a dashboard widget does.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HomeTile(String kind, WidgetLayout layout, String queryId, String chart, Map<String, String> options,
                       String title) {

    public HomeTile {
        options = options == null ? Map.of() : Map.copyOf(options);
    }

    public static HomeTile section(String kind, WidgetLayout layout) {
        return new HomeTile(kind, layout, null, null, null, null);
    }

    public static HomeTile chart(DashboardWidget widget) {
        return new HomeTile("CHART", widget.layout(), widget.queryId(), widget.chart(), widget.options(), widget.title());
    }

    public DashboardWidget asWidget() {
        return new DashboardWidget(queryId, chart, options, title, layout, null);
    }
}
