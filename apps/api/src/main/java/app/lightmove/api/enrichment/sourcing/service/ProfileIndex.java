package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import java.util.List;

/**
 * The profile index: who works where, cheaply, refreshed monthly. Many of its records no longer carry a
 * current title, so it is asked who mentions the function — in the title, past titles or About — and
 * the live index confirms the title.
 */
public interface ProfileIndex {

    boolean isOffered();

    /** Stored beside every record it returns, in the people cache. */
    String provider();

    EnrichmentVendor researchedBy();

    /** One billed search: people at the employer, living in {@code countryCodes}, whose profile names the function. */
    BrightDataPeopleHits whoWorksAt(SearchedEmployer employer, SearchWords words, List<String> countryCodes,
                                    List<String> excludedSlugs, int limit);
}
