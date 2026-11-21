package app.lightmove.api.dashboard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** A dashboard as saved. {@code version} is the one it was opened at, and null when creating one. */
public record SaveDashboardRequest(
        @NotBlank @Size(max = 80) String name,
        boolean shared,
        @NotNull List<@Valid @NotNull DashboardWidgetDto> widgets,
        Long version
) {}
