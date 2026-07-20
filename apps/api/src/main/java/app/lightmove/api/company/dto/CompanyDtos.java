package app.lightmove.api.company.dto;

import java.util.List;

/**
 * The HTTP contract for reads over the company universe (app_lm_companies) — ETL-owned reference
 * data the Strategy screen searches. All responses are derived, never entities: distinct sectors
 * with counts, AI suggestions for a set of sectors, and a match estimate.
 */
public final class CompanyDtos {

    private CompanyDtos() {
    }

    /** One sector (a primary_industry value) and how many companies carry it. */
    public record SectorCount(String name, long count) {}

    public record SectorsResponse(List<SectorCount> sectors) {}

    /** One industry tag and how many companies in the queried sectors carry it. */
    public record TagCount(String tag, long count) {}

    /**
     * Suggestions for a set of chosen (direct) sectors: adjacent sectors from the curated map and
     * inferred tags computed live from tag co-occurrence in the company universe.
     */
    public record SuggestionsResponse(List<String> adjacent, List<TagCount> inferredTags) {}

    public record EstimateResponse(long count) {}
}
