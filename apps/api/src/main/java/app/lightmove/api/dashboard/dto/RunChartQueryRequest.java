package app.lightmove.api.dashboard.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

/** The options to answer a library query with; any left out take the query's default. */
public record RunChartQueryRequest(@Size(max = 8) Map<String, String> options) {}
