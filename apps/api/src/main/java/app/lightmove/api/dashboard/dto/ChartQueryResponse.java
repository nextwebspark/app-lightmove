package app.lightmove.api.dashboard.dto;

import java.util.List;

/**
 * One ready-made chart in the library drawer. {@code charts} are the types its shape allows before data is known;
 * a run's {@code allowedCharts} may be narrower (a pie past eight slices).
 */
public record ChartQueryResponse(String id, String category, String label, String description, String shape,
                                 boolean noSum, boolean staffOnly, String defaultChart, List<String> charts,
                                 List<ChartOptionDto> options) {}
