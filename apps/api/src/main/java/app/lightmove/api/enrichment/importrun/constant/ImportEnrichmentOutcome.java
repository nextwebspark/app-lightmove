package app.lightmove.api.enrichment.importrun.constant;

import java.util.Set;

/** Where one enrichment row stands; every value past {@link #RUNNING} is final. */
public enum ImportEnrichmentOutcome {
    QUEUED,
    RUNNING,
    /** A vendor read answered, and its research was filed. */
    RESEARCHED,
    /** The workspace already held the research, so nothing was bought. */
    ALREADY_RESEARCHED,
    NOT_FOUND,
    /** Suppressed or restricted: researching them again would itself be processing. */
    REFUSED,
    /** Fair use ran out before this read. */
    OVER_ALLOWANCE,
    /** No provider is configured any more, or the row's target has gone. */
    UNAVAILABLE,
    FAILED;

    public static final Set<ImportEnrichmentOutcome> IN_PROGRESS = Set.of(QUEUED, RUNNING);
}
