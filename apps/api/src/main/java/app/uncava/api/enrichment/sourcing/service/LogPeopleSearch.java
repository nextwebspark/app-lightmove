package app.uncava.api.enrichment.sourcing.service;

import app.uncava.api.candidate.constant.EnrichmentVendor;
import app.uncava.api.common.constant.Seniority;
import app.uncava.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.uncava.api.enrichment.sourcing.model.SearchedEmployer;
import app.uncava.api.enrichment.sourcing.model.SourcingSpec;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** The no-vendor stand-in: finds nobody and says the feature is not offered. */
@Slf4j
public class LogPeopleSearch implements PeopleSearch {

    @Override
    public BrightDataPeopleHits currentEmployeesTitled(SearchedEmployer employer, SourcingSpec spec, Seniority seat,
                                                       List<String> countryCodes, List<String> excludedSlugs,
                                                       int size) {
        log.info("People search is off — nobody found at {}", employer.linkedinSlug());
        return BrightDataPeopleHits.of(List.of(), 0L);
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
    public boolean isOffered() {
        return false;
    }
}
