package app.lightmove.api.dashboard.model;

import java.util.Map;

/** Answers one library query for a caller, with options already validated against its declaration. */
@FunctionalInterface
public interface ChartQueryRunner {

    ChartResult run(ChartQueryContext context, Map<String, String> options);
}
