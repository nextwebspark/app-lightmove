package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.JudgedWinner;

/**
 * The judge on one criterion of one case: the side most of the case's pairs went to, {@code pairs} of the
 * {@code of} it judged, and its own reason on the first.
 */
public record EvalCaseJudgedCriterion(String key, String name, JudgedWinner result, int pairs, int of,
                                      String reason) {
}
