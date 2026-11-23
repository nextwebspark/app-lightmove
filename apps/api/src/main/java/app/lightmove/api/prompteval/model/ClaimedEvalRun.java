package app.lightmove.api.prompteval.model;

import java.util.UUID;

/** A run this instance is working, and the token its writes prove that with. */
public record ClaimedEvalRun(EvalRun run, UUID claimToken) {}
