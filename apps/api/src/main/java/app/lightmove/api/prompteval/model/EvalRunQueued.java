package app.lightmove.api.prompteval.model;

import java.util.UUID;

/** A run was stored and waits for a worker. */
public record EvalRunQueued(UUID runId) {}
