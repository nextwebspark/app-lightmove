package app.lightmove.api.dashboard.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A dashboard the reader may open. {@code owned}: theirs to change; a teammate's shared one is read-only. */
public record DashboardResponse(UUID id, String name, boolean shared, boolean owned, List<DashboardWidgetDto> widgets,
                                long version, Instant updatedAt) {}
