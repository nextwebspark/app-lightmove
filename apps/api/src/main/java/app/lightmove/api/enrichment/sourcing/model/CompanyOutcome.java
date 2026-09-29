package app.lightmove.api.enrichment.sourcing.model;

import app.lightmove.api.enrichment.sourcing.constant.SourcingOutcome;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

/**
 * What happened at one company: the outcome, the hits it bought and the hits the people cache answered
 * free, how many matched in all (null when unknown, and on runs recorded before it was kept), the model
 * calls it cost, and the picks with whether each was filed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompanyOutcome(UUID triageCompanyId, String companyName, SourcingOutcome outcome,
                             int vendorHits, int cachedHits, Long matched, int modelCalls, int filed,
                             int skipped, List<ExecutivePick> picks) {

    public static CompanyOutcome of(SourcingCompany company, SourcingOutcome outcome) {
        return new CompanyOutcome(company.triageCompanyId(), company.companyName(), outcome, 0, 0, null, 0, 0, 0, List.of());
    }

    public CompanyOutcome {
        picks = picks == null ? List.of() : List.copyOf(picks);
    }
}
