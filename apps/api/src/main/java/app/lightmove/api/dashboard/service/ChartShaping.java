package app.lightmove.api.dashboard.service;

import app.lightmove.api.dashboard.constant.ChartOptionKey;
import app.lightmove.api.dashboard.constant.ChartShape;
import app.lightmove.api.dashboard.model.ChartQuery;
import app.lightmove.api.dashboard.model.ChartResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * What every answer goes through before it leaves: a series cut to the asked period, a breakdown to its top N, and
 * any breakdown held to {@value #MAX_BUCKETS} labels with the rest folded into Other, so no chart is asked to draw
 * hundreds of slivers.
 */
final class ChartShaping {

    static final int MAX_BUCKETS = 20;
    static final String OTHER = "Other";

    private ChartShaping() {
    }

    static ChartResult shape(ChartQuery query, ChartResult raw, Map<String, String> options) {
        if (raw.shape() == ChartShape.TIME_SERIES) {
            return lastPeriods(raw, options.get(ChartOptionKey.RANGE.key()));
        }
        if (raw.shape() == ChartShape.CATEGORICAL) {
            String top = options.get(ChartOptionKey.TOP.key());
            int keep = top == null || "all".equals(top) ? MAX_BUCKETS : Math.min(Integer.parseInt(top), MAX_BUCKETS);
            return largest(raw, keep, !query.notAdditive(), keep == MAX_BUCKETS);
        }
        return raw;
    }

    private static ChartResult lastPeriods(ChartResult raw, String range) {
        if (range == null || "all".equals(range)) {
            return raw;
        }
        int keep = Integer.parseInt(range);
        int size = raw.labels().size();
        if (size <= keep) {
            return raw;
        }
        return raw.withLabelsAndValues(raw.labels().subList(size - keep, size), raw.values().subList(size - keep, size),
                false);
    }

    private static ChartResult largest(ChartResult raw, int keep, boolean foldRest, boolean capped) {
        if (raw.labels().size() <= keep) {
            return raw;
        }
        List<Integer> order = new ArrayList<>();
        double rest = 0;
        for (int index = 0; index < raw.labels().size(); index++) {
            // An answer that already folded its own tail (the report's sectors) keeps it in the rest: ranked as a
            // group of its own, it drew a second Other beside the one folded here.
            if (foldRest && OTHER.equals(raw.labels().get(index))) {
                rest += raw.values().get(index);
            } else {
                order.add(index);
            }
        }
        order.sort(Comparator.comparing((Integer index) -> raw.values().get(index)).reversed());
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int rank = 0; rank < order.size(); rank++) {
            int index = order.get(rank);
            if (rank < keep) {
                labels.add(raw.labels().get(index));
                values.add(raw.values().get(index));
            } else {
                rest += raw.values().get(index);
            }
        }
        if (foldRest && rest > 0) {
            labels.add(OTHER);
            values.add(rest);
        }
        return raw.withLabelsAndValues(labels, values, capped);
    }
}
