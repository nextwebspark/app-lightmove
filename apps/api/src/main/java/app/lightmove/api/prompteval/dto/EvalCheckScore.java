package app.lightmove.api.prompteval.dto;

/**
 * One check across a run: the share of each version's answers that pass, in percent, as the mean over the repeats,
 * and the candidate's change in points. A side is null when no answer of it gave the check anything to judge.
 */
public record EvalCheckScore(String id, String label, Double candidate, Double baseline, Double change,
                             boolean worseBeyondTolerance) {}
