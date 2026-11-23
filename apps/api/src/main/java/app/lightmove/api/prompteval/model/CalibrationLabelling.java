package app.lightmove.api.prompteval.model;

import java.time.Instant;

/**
 * How far a rubric's pairs are labelled: pairs drawn, pairs with a pick on every criterion, and who labelled last
 * and when. The last two are null with nothing labelled.
 */
public record CalibrationLabelling(int pairs, int labelled, String lastLabelledBy, Instant lastLabelledAt) {}
