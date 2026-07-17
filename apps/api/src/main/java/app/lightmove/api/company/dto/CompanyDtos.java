package app.lightmove.api.company.dto;

import app.lightmove.api.company.constant.TaxonomyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The HTTP contract for company search.
 *
 * <p>{@link Company} is a projection straight off {@code app_lm_companies}, not a JPA entity mapping —
 * that table has no domain behaviour to protect behind an entity, so {@code CompanyRepository} builds
 * this record directly from the JDBC result set.
 */
public final class CompanyDtos {

    private CompanyDtos() {
    }

    public record CompanySearchRequest(
            @NotBlank(message = "Enter a search query")
            @Size(max = 500, message = "That query is too long")
            String query,

            @Min(0) int page,
            @Min(1) @Max(100) int size
    ) {}

    public record CompanySearchResponse(
            CompanySearchFilter resolvedFilter,
            List<TaxonomyMatch> candidates,
            List<Company> companies,
            long totalCompanies,
            int page,
            int size
    ) {}

    /** A taxonomy term the vector-search step considered, and how close it was to the query. */
    public record TaxonomyMatch(TaxonomyType type, String name, Double score) {}

    /**
     * What {@code CompanyIntentExtractor} resolved the query into — the LLM's structured-output target
     * itself, not a separate internal record, since this shape is already exactly what the response
     * needs.
     */
    public record CompanySearchFilter(
            List<String> primaryTypes,
            List<String> tags,
            String country,           // ISO-2, e.g. "AE"
            Integer employeeCountMin,
            Integer employeeCountMax,
            Long revenueMinUsd,
            Long revenueMaxUsd
    ) {}

    public record Company(
            Long id,
            String name,
            String website,
            String domain,
            String primaryIndustry,
            List<String> industryTags,
            String hqCountry,
            Integer employeeCount,
            Long revenueUsd
    ) {}
}
