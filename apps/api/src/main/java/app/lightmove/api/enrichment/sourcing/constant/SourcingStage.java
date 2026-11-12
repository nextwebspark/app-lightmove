package app.lightmove.api.enrichment.sourcing.constant;

/** The step one company's search is on, in the order they run; the panel names each without naming a source. */
public enum SourcingStage {
    /** Counting and reading the people whose titles match the search, and asking who names the function. */
    LOOKING,
    /** Checking free, by name, whether people with no title on record hold the role. */
    CHECKING,
    /** Reading the profiles of the people a check confirmed. */
    CONFIRMING,
    /** Judging and ranking everyone found. */
    RANKING
}
