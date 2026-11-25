package app.lightmove.api.prompteval.model;

import java.util.List;

/**
 * A dataset a release ships: the prompt it scores, what the console calls it, its synthetic cases, and the criteria
 * a judge compares two answers on, empty for a pack no judge scores.
 */
public record EvalSeedPack(String promptId, String name, String description, List<NewEvalCase> cases,
                           List<RubricCriterion> rubric) {

    /** A pack scored by its checks alone. */
    public EvalSeedPack(String promptId, String name, String description, List<NewEvalCase> cases) {
        this(promptId, name, description, cases, List.of());
    }
}
