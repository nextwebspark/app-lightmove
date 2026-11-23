package app.lightmove.api.prompteval.model;

/** What one answer of a prompt has cost in earlier runs. Both null when it has never been run. */
public record EvalCallAverages(Double tokens, Double millis) {}
