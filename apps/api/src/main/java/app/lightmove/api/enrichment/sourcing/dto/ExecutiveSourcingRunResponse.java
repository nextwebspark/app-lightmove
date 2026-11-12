package app.lightmove.api.enrichment.sourcing.dto;

import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRun;
import app.lightmove.api.enrichment.sourcing.model.SourcingCompany;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A run as the panel and the toolbar button read it: where it stands, the companies it takes in order, what each
 * finished one yielded, the step each started one has reached, how many people fit, what it bought, the search as pressed (null on runs from before the
 * panel), and whether a stop was asked for.
 */
public record ExecutiveSourcingRunResponse(UUID id, String status, List<RunCompany> companies, int companiesTotal,
                                           int companiesDone, int suggested, int profilesBought, int profilesCached,
                                           boolean stopRequested, SourcingCriteriaDto searched,
                                           List<SourcingCompanyOutcomeResponse> outcomes,
                                           List<SourcingCompanyProgressResponse> progress, Instant requestedAt,
                                           Instant startedAt, Instant finishedAt, String error) {

    public static ExecutiveSourcingRunResponse of(ExecutiveSourcingRun run) {
        return new ExecutiveSourcingRunResponse(run.getId(), run.getStatus().name(),
                run.getCompanies().stream().map(RunCompany::of).toList(), run.getCompanies().size(),
                run.getCompaniesDone(), run.getSuggested(), run.getVendorHits(), run.getCachedHits(),
                run.isStopRequested(), run.getCriteria() == null ? null : SourcingCriteriaDto.of(run.getCriteria()),
                run.getOutcomes().stream().map(SourcingCompanyOutcomeResponse::of).toList(),
                run.getProgress().entrySet().stream()
                        .map(entry -> SourcingCompanyProgressResponse.of(entry.getKey(), entry.getValue()))
                        .toList(),
                run.getCreatedAt(), run.getStartedAt(), run.getFinishedAt(), run.getError());
    }

    /** A company the run takes; {@code searchable} is false for one with no LinkedIn page. */
    public record RunCompany(UUID triageCompanyId, String companyName, boolean searchable) {

        static RunCompany of(SourcingCompany company) {
            return new RunCompany(company.triageCompanyId(), company.companyName(), company.linkedinSlug() != null);
        }
    }
}
