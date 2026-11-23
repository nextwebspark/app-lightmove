package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.model.EvalRunSummary;

/**
 * A run's page. {@code summary} is null until the run finishes. {@code countsAtPutLive}: a finished run on the
 * held-back cases of a reviewed dataset, the only kind a put live rests on. {@code secondsLeft} is a guess from the
 * pace so far, null unless the run is going and has answered something.
 */
public record EvalRunDetail(
        EvalRunOverview run,
        EvalRunSideDetail candidate,
        EvalRunSideDetail baseline,
        boolean countsAtPutLive,
        int callCap,
        Long secondsLeft,
        EvalRunSummary summary
) {
}
