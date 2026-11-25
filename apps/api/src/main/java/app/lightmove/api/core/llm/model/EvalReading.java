package app.lightmove.api.core.llm.model;

/**
 * One thing an answer says, set beside what the case expects: a competency's score and the evidence for it.
 *
 * @param key      stable within a case, so two answers' readings pair up
 * @param label    what a person calls it: the competency's name
 * @param expected the case's label in words ("7 to 9", "No evidence"), or null when it has none
 * @param score    null when the answer gave none
 * @param text     what the answer says for it: the evidence
 * @param outside  the score is not one the case's label allows
 */
public record EvalReading(String key, String label, String expected, Double score, String text, boolean outside) {}
