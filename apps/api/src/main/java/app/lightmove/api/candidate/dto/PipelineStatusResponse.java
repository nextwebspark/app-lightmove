package app.lightmove.api.candidate.dto;

import java.util.UUID;

/**
 * One status of the catalog. {@code key} is the wire value a candidate's {@code status} carries;
 * {@code system} marks the seven built-in statuses; {@code outOfProcess} sends whoever holds it to Inactive.
 */
public record PipelineStatusResponse(
        UUID id,
        String key,
        String label,
        String colour,
        int position,
        boolean isDefault,
        boolean system,
        boolean outOfProcess,
        boolean retired
) {}
