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
 * One ready-made chart in the library, written and reviewed here. {@code label} may end in " …", which the drawer
 * completes with the chosen group. {@code notAdditive} marks a measure whose parts are no whole (a median, a rate),
 * which no pie may draw. Built through the shape factories, so each library entry reads on its own.
 */
public record ChartQuery(
        String id,
        DashboardScope scope,
        String category,
        String label,
        String description,
        ChartShape shape,
        boolean notAdditive,
        ChartType defaultChart,
        List<ChartOption> options,
        ChartQueryRunner runner
) {

    public ChartQuery {
        options = List.copyOf(options);
    }

    public static ChartQuery kpi(DashboardScope scope, String id, String category, String label, String description,
                                 ChartQueryRunner runner) {
        return new ChartQuery(id, scope, category, label, description, ChartShape.KPI, false, ChartType.KPI, List.of(),
                runner);
    }

    public static ChartQuery breakdown(DashboardScope scope, String id, String category, String label,
                                       String description, ChartType defaultChart, List<ChartOption> options,
                                       ChartQueryRunner runner) {
        return new ChartQuery(id, scope, category, label, description, ChartShape.CATEGORICAL, false, defaultChart,
                options, runner);
    }

    public static ChartQuery overTime(DashboardScope scope, String id, String category, String label,
                                      String description, ChartType defaultChart, List<ChartOption> options,
                                      ChartQueryRunner runner) {
        return new ChartQuery(id, scope, category, label, description, ChartShape.TIME_SERIES, false, defaultChart,
                options, runner);
    }

    public static ChartQuery breakdownByTwo(DashboardScope scope, String id, String category, String label,
                                            String description, ChartQueryRunner runner) {
        return new ChartQuery(id, scope, category, label, description, ChartShape.MATRIX, false, ChartType.STACKED,
                List.of(), runner);
    }

    public static ChartQuery ranked(DashboardScope scope, String id, String category, String label,
                                    String description, ChartQueryRunner runner) {
        return new ChartQuery(id, scope, category, label, description, ChartShape.RANKED, false, ChartType.TABLE,
                List.of(), runner);
    }

    /** This query, its parts no whole: a median or a rate per group. */
    public ChartQuery asNotAdditive() {
        return new ChartQuery(id, scope, category, label, description, shape, true, defaultChart, options, runner);
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
