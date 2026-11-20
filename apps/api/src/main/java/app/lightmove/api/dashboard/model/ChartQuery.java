package app.lightmove.api.dashboard.model;

import app.lightmove.api.dashboard.constant.ChartOptionKey;
import app.lightmove.api.dashboard.constant.ChartShape;
import app.lightmove.api.dashboard.constant.ChartType;
import app.lightmove.api.dashboard.constant.DashboardScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One ready-made chart in the library: written and reviewed here, never assembled from a caller's request.
 * {@code label} may end in " …", which the drawer completes with the chosen group. {@code noSum} marks a measure
 * whose parts do not add up to a whole (a median, a rate), which no pie may draw.
 */
public record ChartQuery(
        String id,
        DashboardScope scope,
        String category,
        String label,
        String description,
        ChartShape shape,
        boolean noSum,
        boolean staffOnly,
        ChartType defaultChart,
        List<ChartOption> options,
        ChartQueryRunner runner
) {

    public ChartQuery {
        options = List.copyOf(options);
    }

    public Optional<ChartOption> option(ChartOptionKey key) {
        return options.stream().filter(option -> option.key() == key).findFirst();
    }

    /** Every option this query offers, each at the value asked for when it is allowed, else at its default. */
    public Map<String, String> settle(Map<String, String> asked) {
        Map<String, String> settled = new LinkedHashMap<>();
        for (ChartOption option : options) {
            String value = asked.get(option.key().key());
            settled.put(option.key().key(), value != null && option.allows(value) ? value : option.defaultValue());
        }
        return settled;
    }
}
