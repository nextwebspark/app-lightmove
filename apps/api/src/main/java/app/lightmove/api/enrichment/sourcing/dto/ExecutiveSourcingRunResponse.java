package app.lightmove.api.enrichment.sourcing.dto;

import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRun;
import app.lightmove.api.enrichment.sourcing.model.SourcingCompany;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A run as the screen polls it: where it stands, what each finished company yielded, what it cost. */
public record ExecutiveSourcingRunResponse(UUID id, String status, List<String> companyNames, int companiesTotal,
                                           int companiesDone, int executivesFiled, int vendorHits, int cachedHits,
                                           SourcingSearchResponse searchedFor,
                                           List<SourcingCompanyOutcomeResponse> outcomes, Instant requestedAt,
                                           Instant startedAt, Instant finishedAt, String error) {

    public static ExecutiveSourcingRunResponse of(ExecutiveSourcingRun run) {
        return new ExecutiveSourcingRunResponse(run.getId(), run.getStatus().name(),
                run.getCompanies().stream().map(SourcingCompany::companyName).toList(),
                run.getCompanies().size(), run.getCompaniesDone(), run.getExecutivesFiled(), run.getVendorHits(), run.getCachedHits(),
                SourcingSearchResponse.of(run.getSpec()),
                run.getOutcomes().stream().map(SourcingCompanyOutcomeResponse::of).toList(),
                run.getCreatedAt(), run.getStartedAt(), run.getFinishedAt(), run.getError());
    }
}
