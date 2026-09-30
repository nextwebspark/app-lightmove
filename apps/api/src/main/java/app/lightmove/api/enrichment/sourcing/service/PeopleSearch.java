package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.List;

/**
 * Finds people at a company whose current title fits. A port so the run tests end to end with no
 * vendor, and so Bright Data's dataset and ContactOut's index answer the same run; every hit a real
 * provider returns is billed, so a caller asks for a handful. Hits arrive in Bright Data's record shape
 * whichever provider answered — the people cache, the ranking and the filing all read that one shape.
 */
public interface PeopleSearch {

    /**
     * @param employer      the company, in every key a provider may search it by
     * @param spec          the title words: one seniority word, one function word, none of the excluded
     * @param seat          the brief's level, for a provider that searches the nearest titles first; may be null
     * @param countryCodes  ISO-2 residence codes to narrow to; empty means anywhere
     * @param excludedSlugs profile slugs not to return — people already on file, never bought twice
     * @param size          the most records to return, each of them billed
     * @return the hits, and how many matched in all when the provider says
     */
    BrightDataPeopleHits currentEmployeesTitled(SearchedEmployer employer, SourcingSpec spec, Seniority seat,
                                                List<String> countryCodes, List<String> excludedSlugs, int size);

    /** Stored beside every record a search returned, in the people cache. */
    String provider();

    /** Recorded on every executive filed from a hit, as the vendor behind their research. */
    EnrichmentVendor researchedBy();

    /** False for the no-vendor stand-in: the screen then offers no Find executives at all. */
    default boolean isOffered() {
        return true;
    }
}
