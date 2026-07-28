package app.lightmove.api.project.dto;

import java.util.List;

/**
 * The HTTP contract for a project's Sourcing results: the company universe filtered by that project's
 * saved Strategy scope (sectors + company size), one page at a time.
 */
public final class SourcingDtos {

    private SourcingDtos() {
    }

    /** One matching company, projected down to what the Sourcing list shows. */
    public record CompanyResultDto(long id, String name, String domain, String sector,
                                    String employeeRange, String revenueRange, String location,
                                    String matchTier) {}

    /**
     * Which of the scope categories the query actually filtered on. Every returned company is
     * guaranteed to satisfy each {@code true} category (the query ANDs them together) — this isn't a
     * per-company fit score, just which of the criteria the card's checkmarks should render at all.
     */
    public record AppliedFilters(boolean sector, boolean employee, boolean revenue, boolean geography) {}

    public record SourcingResponse(List<CompanyResultDto> companies, long totalCount, int page, int size,
                                    AppliedFilters appliedFilters) {}

    // ── CoreSignal run endpoints (POC) ──────────────────────────────────────────────────────────

    /**
     * One company collected from CoreSignal — everything the card AND the detail drawer show, so
     * the poll response is the drawer's whole data source and no per-company endpoint exists.
     */
    public record SourcedCompanyDto(long coresignalId, String name, String website, String linkedinUrl,
                                    String logoUrl, String industry, String sizeRange,
                                    Integer employeesCount, String revenueRange, Long revenueAnnualUsd,
                                    String location, String country, Integer foundedYear,
                                    String description, String matchTier) {}

    /**
     * A poll of the current run. {@code companies} holds what is collected so far, already in the
     * provider's revenue-desc order — during COLLECTING it simply grows toward
     * {@code requestedCount}, never reorders. {@code searchedCount} is how many ids the search
     * kept: {@code requestedCount < searchedCount} is what makes "load more" worth offering
     * ({@code totalMatched} counts matches beyond the kept ids too). {@code criteriaMatchesStrategy}
     * false tells the SPA these results describe an older scope and a fresh start is wanted.
     */
    public record SourcingRunDto(String status, int requestedCount, int collectedCount,
                                 int searchedCount, long totalMatched,
                                 boolean criteriaMatchesStrategy, String error,
                                 List<SourcedCompanyDto> companies) {}

    /** {@code run} is null when the project has never run CoreSignal sourcing — the SPA auto-starts. */
    public record SourcingRunResponse(SourcingRunDto run) {}
}
