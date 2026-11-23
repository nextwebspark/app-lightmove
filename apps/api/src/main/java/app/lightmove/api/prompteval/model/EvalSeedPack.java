package app.lightmove.api.prompteval.model;

import java.util.List;

/** A dataset a release ships: the prompt it scores, what the console calls it, and its synthetic cases. */
public record EvalSeedPack(String promptId, String name, String description, List<NewEvalCase> cases) {}
