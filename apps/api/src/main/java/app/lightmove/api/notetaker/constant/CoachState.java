package app.lightmove.api.notetaker.constant;

/**
 * Whether the coach's suggestions are current: running, paused after failed runs (retried on new speech), skipping a
 * stretch the prompt guard stopped, or out of runs for this call.
 */
public enum CoachState {
    RUNNING,
    PAUSED,
    GUARDED,
    CAPPED
}
