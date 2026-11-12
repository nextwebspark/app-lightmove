package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import java.util.List;

/** No profile index on this deployment: never offered, and answers nobody if asked. */
public class UnconfiguredProfileIndex implements ProfileIndex {

    @Override
    public boolean isOffered() {
        return false;
    }

    @Override
    public String provider() {
        return "off";
    }

    @Override
    public EnrichmentVendor researchedBy() {
        return EnrichmentVendor.BRIGHTDATA;
    }

    @Override
    public BrightDataPeopleHits whoWorksAt(SearchedEmployer employer, SearchWords words, List<String> countryCodes,
                                           List<String> excludedSlugs, int limit) {
        return BrightDataPeopleHits.of(List.of(), 0L);
    }
}
