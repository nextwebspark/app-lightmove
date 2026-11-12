package app.lightmove.api.enrichment.sourcing.dto;

import app.lightmove.api.enrichment.sourcing.model.CompanyOutcome;
import java.util.UUID;

/**
 * One company's result, as the panel's progress line and "How we searched" read it: the outcome, the people found
 * and how they were judged, the live profiles bought and read back free, what the live index matched (null when
 * unknown), what the profile index answered and the free title checks made.
 */
public record SourcingCompanyOutcomeResponse(UUID triageCompanyId, String companyName, String outcome, int found,
                                             int fits, int noTitle, int notFit, int bought, int cached,
                                             Long matched, int indexFound, int titleChecks) {

    public static SourcingCompanyOutcomeResponse of(CompanyOutcome outcome) {
        return new SourcingCompanyOutcomeResponse(outcome.triageCompanyId(), outcome.companyName(),
                outcome.outcome().name(), outcome.found(), outcome.fits(), outcome.noTitle(), outcome.notFit(),
                outcome.vendorHits(), outcome.cachedHits(), outcome.matched(), outcome.indexFound(),
                outcome.titleChecks());
    }
}
