package app.lightmove.api.dashboard.dto;

import app.lightmove.api.dashboard.model.ChartKpi;
import app.lightmove.api.dashboard.model.ChartSeries;
import java.util.List;
import java.util.Map;

/**
 * A library query's answer as the SPA draws it: the shape's fields filled, the rest empty. {@code allowedCharts}
 * are the types that may draw this very answer; {@code options} the values it was answered with;
 * {@code folded} that the smallest groups were folded into Other.
 */
public record ChartResultResponse(
        String queryId,
        String shape,
        List<String> allowedCharts,
        Map<String, String> options,
        String dimension,
        String measure,
        String unit,
        List<String> labels,
        List<Double> values,
        List<ChartSeries> series,
        List<Boolean> suppressed,
        ChartKpi kpi,
        List<String> columns,
        List<List<String>> rows,
        String note,
        boolean percent,
        boolean truncated,
        boolean folded
) {}
