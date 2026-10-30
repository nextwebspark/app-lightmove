package app.lightmove.api.candidate.dto;

import java.util.List;
import java.util.UUID;

/** One stage of the catalog. {@code system} marks the seeded stages. */
public record PipelineStageResponse(
        UUID id,
        String key,
        String label,
        String colour,
        int position,
        boolean system,
        boolean retired,
        List<PipelineStatusResponse> statuses
) {}
