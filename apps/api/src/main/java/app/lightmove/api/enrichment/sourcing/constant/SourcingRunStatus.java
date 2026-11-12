package app.lightmove.api.enrichment.sourcing.constant;

import java.util.Set;

/** Where a Find executives run stands. Stored by name, matching V213's CHECK. */
public enum SourcingRunStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    /** Stopped on request: the companies in flight finished, the rest were not searched. */
    STOPPED;

    /** The two states in which a second run of the same mandate is refused. */
    public static final Set<SourcingRunStatus> IN_PROGRESS = Set.of(QUEUED, RUNNING);
}
