package app.lightmove.api.enrichment.sourcing.constant;

/** What a run found at one company. FILED and ALL_ALREADY_MAPPED are read back from runs that filed on their own. */
public enum SourcingOutcome {
    /** Someone found fits the role. */
    FOUND,
    /** At least one executive was filed. */
    FILED,
    /** The company row carries no LinkedIn page, so there is nothing to key the search on. */
    NO_LINKEDIN_PAGE,
    /** The vendor holds nobody at this company with a fitting title. */
    NO_HITS,
    /** Every hit was already mapped in the mandate. */
    ALL_ALREADY_MAPPED,
    /** People were found, but none fits the role. */
    NOTHING_FIT,
    /** The vendor or the model threw; the other companies were unaffected. */
    FAILED,
    /** The run's deadline passed before this company was searched. */
    NOT_REACHED,
    /** The run was stopped before this company was searched. */
    STOPPED
}
