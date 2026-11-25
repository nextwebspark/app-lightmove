package app.lightmove.api.prompteval.constant;

/** Where an eval run has got to. A cancelled or failed run gives no result. */
public enum EvalRunStatus {
    QUEUED,
    RUNNING,
    FINISHED,
    CANCELLED,
    FAILED
}
