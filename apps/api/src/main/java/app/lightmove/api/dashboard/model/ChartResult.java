package app.lightmove.api.dashboard.model;

import app.lightmove.api.dashboard.constant.ChartShape;
import java.util.List;

/**
 * A library query's answer, in the one shape it declares. Fields another shape does not use are empty, never
 * null. {@code suppressed} marks labels held back because too few people stand behind them; their values are
 * zeroed before the result leaves the server. {@code percent} asks a stacked bar to draw shares, not counts.
 */
public record ChartResult(
        ChartShape shape,
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
) {

    public ChartResult {
        labels = List.copyOf(labels);
        values = List.copyOf(values);
        series = List.copyOf(series);
        suppressed = List.copyOf(suppressed);
        columns = List.copyOf(columns);
        rows = rows.stream().map(List::copyOf).toList();
    }

    public static ChartResult kpi(String value, String unit, String sub, boolean alarm) {
        return new ChartResult(ChartShape.KPI, "", "", "", List.of(), List.of(), List.of(), List.of(),
                new ChartKpi(value, unit, sub, alarm), List.of(), List.of(), null, false, false, false);
    }

    public static ChartResult categorical(String dimension, String measure, String unit, List<String> labels,
                                          List<Double> values) {
        return new ChartResult(ChartShape.CATEGORICAL, dimension, measure, unit, labels, values, List.of(),
                List.of(), null, List.of(), List.of(), null, false, false, false);
    }

    public static ChartResult timeSeries(String dimension, String measure, List<String> labels, List<Double> values) {
        return new ChartResult(ChartShape.TIME_SERIES, dimension, measure, "", labels, values, List.of(),
                List.of(), null, List.of(), List.of(), null, false, false, false);
    }

    public static ChartResult matrix(String dimension, List<String> labels, List<ChartSeries> series,
                                     List<Boolean> suppressed, boolean percent, String note) {
        return new ChartResult(ChartShape.MATRIX, dimension, "", "", labels, List.of(), series, suppressed, null,
                List.of(), List.of(), note, percent, false, false);
    }

    public static ChartResult ranked(List<String> columns, List<List<String>> rows) {
        return new ChartResult(ChartShape.RANKED, "", "", "", List.of(), List.of(), List.of(), List.of(), null,
                columns, rows, null, false, false, false);
    }

    public ChartResult withLabelsAndValues(List<String> newLabels, List<Double> newValues, boolean cut) {
        return new ChartResult(shape, dimension, measure, unit, newLabels, newValues, series, suppressed, kpi,
                columns, rows, note, percent, truncated || cut, folded);
    }

    /** This answer with its tail folded into Other — said by the server, so no screen guesses it from a label. */
    public ChartResult withLabelsAndValuesFolded(List<String> newLabels, List<Double> newValues, boolean cut) {
        return new ChartResult(shape, dimension, measure, unit, newLabels, newValues, series, suppressed, kpi,
                columns, rows, note, percent, truncated || cut, true);
    }

    /** This answer with {@code extra} after any note it already carries. */
    public ChartResult withNoteAppended(String extra) {
        String joined = note == null || note.isBlank() ? extra : note + " " + extra;
        return new ChartResult(shape, dimension, measure, unit, labels, values, series, suppressed, kpi, columns, rows,
                joined, percent, truncated, folded);
    }

    public ChartResult withMatrix(List<String> newLabels, List<ChartSeries> newSeries, List<Boolean> newSuppressed) {
        return new ChartResult(shape, dimension, measure, unit, newLabels, values, newSeries, newSuppressed, kpi,
                columns, rows, note, percent, truncated, folded);
    }
}
