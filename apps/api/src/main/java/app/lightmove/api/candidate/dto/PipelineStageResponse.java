package app.lightmove.api.candidate.dto;

import java.util.List;
import java.util.UUID;

/** One stage of the catalog. {@code system} marks the six seeded stages; {@code kind} is active or closed. */
public record PipelineStageResponse(
        UUID id,
        String key,
        String label,
        String colour,
        String kind,
        int position,
        boolean system,
        boolean retired,
        List<PipelineStatusResponse> statuses
) {}
