package app.lightmove.api.candidate.constant;

/**
 * Which paid provider answered a profile's research — recorded for querying, never branched on. Not
 * {@link CandidateSource}, the door the row came through.
 */
public enum EnrichmentVendor {

    /** Bright Data's stored LinkedIn dataset — the primary lookup. */
    BRIGHTDATA,

    /** HarvestAPI's live scrape — the fallback when the dataset misses, is thin, or fails. */
    HARVESTAPI,

    /** ContactOut's people index — a Find executives hit when the run searches it (V90). */
    CONTACTOUT
}
