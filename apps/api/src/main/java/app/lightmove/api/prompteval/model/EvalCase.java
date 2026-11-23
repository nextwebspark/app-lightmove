package app.lightmove.api.prompteval.model;

import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import java.time.Instant;
import java.util.UUID;

/** A case as a list shows it: never its input. {@code addedBy} is null for one shipped with a release. */
public record EvalCase(UUID id, UUID datasetId, String key, String name, String tag, EvalCaseSplit split,
                       String addedBy, Instant addedAt, Instant retiredAt) {}
