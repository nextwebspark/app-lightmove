package app.lightmove.api.candidate.constant;

/**
 * Which paid provider answered the research behind a profile — recorded so the split between the
 * dataset and the live scrape behind it is a query rather than a guess, and never branched on.
 *
 * <p>Distinct from {@link CandidateSource}, which is the door the row itself came through: a plugin
 * capture is the only source that is researched, and the vendor that answered is the vendor of the
 * minute, not a property of the capture.
 */
public enum EnrichmentVendor {

    /** Bright Data's stored LinkedIn dataset — the primary lookup. */
    BRIGHTDATA,

    /** HarvestAPI's live scrape — the fallback when the dataset misses, is thin, or fails. */
    HARVESTAPI
}
