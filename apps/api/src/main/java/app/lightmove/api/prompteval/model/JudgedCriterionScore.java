package app.lightmove.api.prompteval.model;

/**
 * One criterion across a run, counted in pairs. {@code net} is the share of judged pairs the candidate won less the
 * share the baseline won, in points; null when no pair was judged. {@code unjudged} pairs count for neither side.
 */
public record JudgedCriterionScore(String key, String name, int candidateWon, int baselineWon, int ties, int unjudged,
                                   Double net, boolean worseBeyondTolerance, boolean betterBeyondTolerance) {}
