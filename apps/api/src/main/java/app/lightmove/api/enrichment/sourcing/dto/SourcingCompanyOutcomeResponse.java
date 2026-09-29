package app.lightmove.api.enrichment.sourcing.dto;

import app.lightmove.api.enrichment.sourcing.model.CompanyOutcome;
import java.util.List;
import java.util.UUID;

/**
 * One company's line of the strip. {@code seen} is how many people the search answered with, and
 * {@code matched} how many fitted in all (null when unknown) — more matched than seen means the
 * per-company cap cut it short.
 */
public record SourcingCompanyOutcomeResponse(UUID triageCompanyId, String companyName, String outcome,
                                             int seen, Long matched, int filed, int skipped,
                                             List<SourcingPickResponse> picks) {

    static SourcingCompanyOutcomeResponse of(CompanyOutcome outcome) {
        return new SourcingCompanyOutcomeResponse(outcome.triageCompanyId(), outcome.companyName(),
                outcome.outcome().name(), outcome.vendorHits() + outcome.cachedHits(), outcome.matched(),
                outcome.filed(), outcome.skipped(),
                outcome.picks().stream().map(SourcingPickResponse::of).toList());
    }
}
