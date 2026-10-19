package app.lightmove.api.candidate.model;

/**
 * The stage positions the progress counts measure from: {@code startingAt} is where new people start, so reached
 * out is past it; {@code interestedFrom} is the stage of the seeded Interested status, so engaged is from it on —
 * {@link Integer#MAX_VALUE} once that stage is retired, rather than a boundary nobody drew.
 */
public record PipelineProgressAnchors(int startingAt, int interestedFrom) {}
