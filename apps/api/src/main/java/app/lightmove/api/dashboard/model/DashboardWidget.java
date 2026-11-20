package app.lightmove.api.dashboard.model;

import java.util.Map;

/**
 * One chart on a dashboard as stored (V231): the library query it draws, how ({@code chart}, a {@code ChartType}
 * token), its options, an optional title and its {@code size} token. Never a figure.
 */
public record DashboardWidget(String queryId, String chart, Map<String, String> options, String title, String size) {

    public DashboardWidget {
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}
