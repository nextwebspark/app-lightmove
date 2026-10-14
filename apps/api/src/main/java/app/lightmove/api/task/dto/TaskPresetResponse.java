package app.lightmove.api.task.dto;

import java.time.Instant;
import java.util.UUID;

/** One preset of the workspace's catalog, with how many tasks were filed from it. */
public record TaskPresetResponse(UUID id, String label, String kind, Integer dueOffsetDays, int position,
                                 boolean retired, Instant retiredAt, long usage) {}
