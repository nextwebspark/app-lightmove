package app.lightmove.api.enrichment.sourcing.constant;

/** What a run found at one company — one line of the progress strip. */
public enum SourcingOutcome {
    /** At least one executive was filed. */
    FILED,
    /** The company row carries no LinkedIn page, so there is nothing to key the search on. */
    NO_LINKEDIN_PAGE,
    /** The vendor holds nobody at this company with a fitting title. */
    NO_HITS,
    /** Every hit was already mapped in the mandate. */
    ALL_ALREADY_MAPPED,
    /** Hits came back, but the model chose none — or every pick collided with a held name. */
    NOTHING_FIT,
    /** The vendor or the model threw; the other companies were unaffected. */
    FAILED,
    /** The run's deadline passed before this company was searched. */
    NOT_REACHED
}
