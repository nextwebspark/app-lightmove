package app.lightmove.api.prompteval.constant;

/** Where a check of the judge against a rubric's labels has got to. */
public enum CalibrationCheckStatus {
    QUEUED,
    RUNNING,
    FINISHED,
    FAILED
}
