package app.lightmove.api.prompteval.model;

/**
 * One thing the judge compares two answers on. {@code key} is stable within a rubric and stored with every pick;
 * {@code better} says what the better answer does.
 */
public record RubricCriterion(String key, String name, String better) {}
