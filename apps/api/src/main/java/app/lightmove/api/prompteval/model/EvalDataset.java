package app.lightmove.api.prompteval.model;

import java.time.Instant;
import java.util.UUID;

/** A dataset with the counts of the cases still in use. A retired case is in none of the three. */
public record EvalDataset(UUID id, String promptId, String name, String description, int cases, int dev,
                          int holdout, Instant updatedAt) {}
