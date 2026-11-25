package app.lightmove.api.prompteval.model;

import java.util.UUID;

/** A check of the judge was stored and waits for a worker. */
public record CalibrationCheckQueued(UUID checkId) {}
