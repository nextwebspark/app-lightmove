package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import java.time.Instant;
import java.util.UUID;

/** A case as a dataset lists it. {@code addedBy} is null for one shipped with a release. */
public record EvalCaseSummary(
        UUID id,
        String key,
        String name,
        String tag,
        EvalCaseSplit split,
        String addedBy,
        Instant addedAt,
        Instant retiredAt
) {
}
