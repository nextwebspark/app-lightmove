package app.lightmove.api.prompteval.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A dataset as the list shows it. The three counts leave retired cases out. {@code importable} is false for a
 * prompt with no eval subject, whose cases can be read but not added to or run. {@code rubric} is null for a
 * dataset no judge scores.
 */
public record EvalDatasetOverview(
        UUID id,
        String name,
        String description,
        String promptId,
        String promptName,
        int cases,
        int dev,
        int holdout,
        Instant updatedAt,
        boolean importable,
        EvalRubricSummary rubric
) {
}
