package app.lightmove.api.prompteval.constant;

/**
 * Which cases a prompt is worked on with, and which are kept back: the check before a version goes live scores on
 * {@link #HOLDOUT}, so a prompt cannot be tuned to pass it.
 */
public enum EvalCaseSplit {
    DEV,
    HOLDOUT
}
