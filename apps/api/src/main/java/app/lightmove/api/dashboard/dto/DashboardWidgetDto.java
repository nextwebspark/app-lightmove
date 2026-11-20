package app.lightmove.api.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** One chart on a dashboard: a library query, a {@code ChartType} token, its options, a title and a size token. */
public record DashboardWidgetDto(
        @NotBlank @Size(max = 64) String queryId,
        @NotBlank @Size(max = 16) String chart,
        @Size(max = 8) Map<String, String> options,
        @Size(max = 80) String title,
        @NotBlank @Size(max = 1) String size
) {}
