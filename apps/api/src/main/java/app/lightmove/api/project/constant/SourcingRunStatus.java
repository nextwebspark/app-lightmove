package app.lightmove.api.project.constant;

/**
 * Lifecycle of one CoreSignal sourcing run, as the polling UI reads it. PENDING and SEARCHING are
 * brief (one search request); COLLECTING is where a run spends its time, with results streaming
 * into the cache as parallel collects land; READY and FAILED are terminal until the criteria
 * change or the user retries.
 */
public enum SourcingRunStatus {
    PENDING,
    SEARCHING,
    COLLECTING,
    READY,
    FAILED;

    /** Still moving — the frontend keeps polling exactly while this is true. */
    public boolean isActive() {
        return this == PENDING || this == SEARCHING || this == COLLECTING;
    }
}
