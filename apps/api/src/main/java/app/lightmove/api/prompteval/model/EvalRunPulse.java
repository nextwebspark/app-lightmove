package app.lightmove.api.prompteval.model;

/**
 * What a worker learns each time it shows a run is still being worked. {@code held} false: the run ended or another
 * instance took it over, and this worker must write nothing more.
 */
public record EvalRunPulse(boolean held, boolean stopRequested, int callsMade) {

    public static final EvalRunPulse LOST = new EvalRunPulse(false, false, 0);
}
