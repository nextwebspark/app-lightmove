package app.lightmove.api.dashboard.model;

import java.util.List;

/** One series of a two-way breakdown, its values in the order of the result's labels. */
public record ChartSeries(String name, List<Double> values) {

    public ChartSeries {
        values = List.copyOf(values);
    }
}
