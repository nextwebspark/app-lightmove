package app.lightmove.api.prompteval.dto;

/**
 * One criterion of a rubric. {@code agreed} of {@code of} labelled pairs are those the judge picked as the person
 * did at its last finished check; both null when it has never been checked.
 */
public record EvalRubricCriterionDetail(String key, String name, String better, Integer agreed, Integer of) {
}
