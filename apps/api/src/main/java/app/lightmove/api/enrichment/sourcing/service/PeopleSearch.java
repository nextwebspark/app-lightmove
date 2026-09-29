package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.List;

/**
 * Finds people at a company whose current title fits. A port so the run tests end to end with no
 * vendor; every hit a real provider returns is billed, so a caller asks for a handful.
 */
public interface PeopleSearch {

    /**
     * @param companySlug   the employer's LinkedIn slug — the people dataset's own company key
     * @param spec          the title words: one seniority word, one function word, none of the excluded
     * @param countryCodes  ISO-2 residence codes to narrow to; empty means anywhere
     * @param excludedSlugs profile slugs not to return — people already on file, never bought twice
     * @param size          the most records to return, each of them billed
     * @return the hits, and how many matched in all when the provider says
     */
    BrightDataPeopleHits currentEmployeesTitled(String companySlug, SourcingSpec spec, List<String> countryCodes,
                                                List<String> excludedSlugs, int size);

    /** Stored beside every record a search returned, in the people cache. */
    String provider();

    /** False for the no-vendor stand-in: the screen then offers no Find executives at all. */
    default boolean isOffered() {
        return true;
    }
}
