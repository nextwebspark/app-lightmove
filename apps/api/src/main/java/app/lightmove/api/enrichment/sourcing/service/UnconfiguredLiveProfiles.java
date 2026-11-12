package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.TitleTier;
import java.util.List;

/** No live index on this deployment: never offered, and answers nobody if asked. */
public class UnconfiguredLiveProfiles implements LiveProfiles {

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
        return EnrichmentVendor.CONTACTOUT;
    }

    @Override
    public long count(SearchedEmployer employer, TitleTier tier, List<String> countryCodes) {
        return 0;
    }

    @Override
    public long countAcross(List<SearchedEmployer> employers, SearchWords words, List<String> countryCodes) {
        return 0;
    }

    @Override
    public BrightDataPeopleHits search(SearchedEmployer employer, TitleTier tier, List<String> countryCodes,
                                       int size) {
        return BrightDataPeopleHits.of(List.of(), 0L);
    }

    @Override
    public boolean holdsTitle(SearchedEmployer employer, String personName, SearchWords words) {
        return false;
    }

    @Override
    public BrightDataPeopleHits profileOf(SearchedEmployer employer, String personName, SearchWords words) {
        return BrightDataPeopleHits.of(List.of(), 0L);
    }
}
