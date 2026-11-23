package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.EvalCheckVerdict;
import tools.jackson.databind.JsonNode;

/**
 * One deterministic rule an answer is held to. The same answer always gets the same verdict, so a change in a
 * check's pass rate between two versions is a change in the answers.
 *
 * @param id    stable across releases: stored with every result, so a renamed check still reads its old runs
 * @param label what the rule holds an answer to, as a compare view titles its row
 */
public record EvalCheck(String id, String label, Rule rule) {

    public static EvalCheck of(String id, String label, Rule rule) {
        return new EvalCheck(id, label, rule);
    }

    /**
     * @param answer what the model answered, as the feature bound it; null when it answered nothing the feature
     *               could read, which a check fails unless the case gives it nothing to judge
     */
    public EvalCheckVerdict verdictOn(JsonNode input, JsonNode expected, JsonNode answer) {
        return rule.verdictOn(input, expected, answer);
    }

    @FunctionalInterface
    public interface Rule {
        EvalCheckVerdict verdictOn(JsonNode input, JsonNode expected, JsonNode answer);
    }
}
