package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.EvalCheckVerdict;
import tools.jackson.databind.JsonNode;

/**
 * One deterministic rule an answer is held to. The same answer always gets the same verdict, so a change in a
 * check's pass rate between two versions is a change in the answers.
 */
public interface EvalCheck {

    /** Stable across releases: stored with every result, so a renamed check still reads its old runs. */
    String id();

    /** What the rule holds an answer to, as a compare view titles its row. */
    String label();

    /**
     * @param answer what the model answered, as the feature bound it; null when it answered nothing the feature
     *               could read, which a check fails unless the case gives it nothing to judge
     */
    EvalCheckVerdict verdictOn(JsonNode input, JsonNode expected, JsonNode answer);

    static EvalCheck of(String id, String label, Rule rule) {
        return new EvalCheck() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public EvalCheckVerdict verdictOn(JsonNode input, JsonNode expected, JsonNode answer) {
                return rule.verdictOn(input, expected, answer);
            }
        };
    }

    /** A check's rule on its own, for one written as a method reference. */
    @FunctionalInterface
    interface Rule {
        EvalCheckVerdict verdictOn(JsonNode input, JsonNode expected, JsonNode answer);
    }
}
