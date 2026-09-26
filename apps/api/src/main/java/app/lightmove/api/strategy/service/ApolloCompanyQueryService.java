package app.lightmove.api.strategy.service;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.strategy.constant.CompanySizeBand;
import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.EmployeeBand;
import app.lightmove.api.strategy.constant.RevenueBand;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.strategy.dto.FacetCount;
import app.lightmove.api.strategy.dto.SectorGroup;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.NumericRange;
import app.lightmove.api.strategy.model.ScopeBreakdown;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Every read of the ETL-owned, read-only company universe {@code app_lm_apollo_companies}. Apollo ships
 * size as raw figures and revenue sparsely, hence numeric band ranges and a selectable Unknown band.
 * Facet counts span the whole universe, so no filter invalidates them.
 */
@Service
@RequiredArgsConstructor
public class ApolloCompanyQueryService {

    /** Every column the list and the write-path snapshots need, in one place so they cannot drift. */
    private static final String ROW_COLUMNS = """
            apollo_account_id, company_name, industry, company_country, company_city,
            num_employees, annual_revenue, website, logo_url,
            short_description, founded_year,
            company_linkedin_url, facebook_url, twitter_url,
            company_phone, company_state, company_address, parent_company,
            total_funding, latest_funding, latest_funding_amount, last_raised_at,
            number_of_retail_locations, keywords, technologies, sic_codes, naics_codes""";

    private final JdbcClient jdbc;
    private final SectorTaxonomy taxonomy;
    private final MarketSegments marketSegments;

    /** An empty scope is the whole universe, not nothing. */
    public long count(CompanyScope scope) {
        WhereClause where = buildWhere(scope);
        return jdbc.sql("SELECT count(*) FROM app_lm_apollo_companies a WHERE " + where.sql())
                .params(where.params()).query(Long.class).single();
    }

    public List<CompanyRow> search(CompanyScope scope, CompanySortField sort, SortDirection direction,
                                   int page, int size) {
        WhereClause where = buildWhere(scope);
        Map<String, Object> params = new LinkedHashMap<>(where.params());
        String sql = """
                SELECT %s
                FROM app_lm_apollo_companies a
                WHERE %s
                ORDER BY %s, apollo_account_id
                LIMIT :size OFFSET :offset
                """.formatted(ROW_COLUMNS, where.sql(), sort.orderByTerms(direction));
        params.put("size", size);
        params.put("offset", (long) page * size);
        return jdbc.sql(sql).params(params).query(COMPANY_ROW_MAPPER).list();
    }

    /** The write path's seam: only a company the universe holds can be snapshotted. */
    public List<CompanyRow> byAccountIds(List<String> apolloAccountIds) {
        if (apolloAccountIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT %s
                        FROM app_lm_apollo_companies
                        WHERE apollo_account_id IN (:ids)
                        """.formatted(ROW_COLUMNS))
                .param("ids", apolloAccountIds)
                .query(COMPANY_ROW_MAPPER)
                .list();
    }

    /** Ranked prefix match first, then by size: three letters usually mean the biggest match. */
    public List<CompanyRow> typeahead(String query, int limit) {
        String pattern = escapeLikePattern(query);
        return jdbc.sql("""
                        SELECT %s
                        FROM app_lm_apollo_companies
                        WHERE company_name ILIKE :contains ESCAPE '\\'
                        ORDER BY (company_name ILIKE :prefix ESCAPE '\\') DESC,
                                 num_employees DESC NULLS LAST,
                                 company_name
                        LIMIT :limit
                        """.formatted(ROW_COLUMNS))
                .param("contains", "%" + pattern + "%")
                .param("prefix", pattern + "%")
                .param("limit", limit)
                .query(COMPANY_ROW_MAPPER)
                .list();
    }

    /**
     * The LinkedIn slug is the strong key; an exact name is trusted only when unique, because mapping
     * the wrong "Alpha" is worse than mapping none. Never fuzzy.
     */
    public Optional<CompanyRow> matchEmployer(String linkedInSlug, String companyName) {
        if (linkedInSlug != null && !linkedInSlug.isBlank()) {
            String pattern = escapeLikePattern(linkedInSlug.toLowerCase(Locale.ROOT));
            List<CompanyRow> bySlug = jdbc.sql("""
                            SELECT %s
                            FROM app_lm_apollo_companies
                            WHERE lower(company_linkedin_url) LIKE :exact ESCAPE '\\'
                               OR lower(company_linkedin_url) LIKE :trailing ESCAPE '\\'
                            LIMIT 1
                            """.formatted(ROW_COLUMNS))
                    .param("exact", "%linkedin.com/company/" + pattern)
                    .param("trailing", "%linkedin.com/company/" + pattern + "/%")
                    .query(COMPANY_ROW_MAPPER)
                    .list();
            if (!bySlug.isEmpty()) {
                return Optional.of(bySlug.getFirst());
            }
        }
        if (companyName == null || companyName.isBlank()) {
            return Optional.empty();
        }
        List<CompanyRow> byName = jdbc.sql("""
                        SELECT %s
                        FROM app_lm_apollo_companies
                        WHERE lower(company_name) = lower(:name)
                        LIMIT 2
                        """.formatted(ROW_COLUMNS))
                .param("name", companyName)
                .query(COMPANY_ROW_MAPPER)
                .list();
        return byName.size() == 1 ? Optional.of(byName.getFirst()) : Optional.empty();
    }

    /**
     * The biggest company of that exact name in {@code country} (anywhere when null) with at least
     * {@code minEmployees}; unlike {@link #matchEmployer} the name need not be unique.
     */
    public Optional<CompanyRow> largestNamed(String companyName, String country, int minEmployees) {
        if (companyName == null || companyName.isBlank()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        SELECT %s
                        FROM app_lm_apollo_companies
                        WHERE lower(company_name) = lower(:name)
                          AND (CAST(:country AS text) IS NULL OR company_country = :country)
                          AND coalesce(num_employees, 0) >= :min
                        ORDER BY num_employees DESC NULLS LAST
                        LIMIT 1
                        """.formatted(ROW_COLUMNS))
                .param("name", companyName.strip())
                .param("country", country)
                .param("min", minEmployees)
                .query(COMPANY_ROW_MAPPER)
                .optional();
    }

    /**
     * In the taxonomy's file order. An industry the taxonomy does not cover is dropped;
     * {@code SectorTaxonomyCoverageIntegrationTest} asserts there is none.
     */
    public List<SectorGroup> sectorGroups() {
        Map<String, Long> countByIndustry = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT industry AS label, count(*) AS count
                        FROM app_lm_apollo_companies
                        WHERE industry IS NOT NULL AND industry <> ''
                        GROUP BY 1
                        """)
                .query(ScopeBreakdown.class)
                .list()
                .forEach(row -> countByIndustry.put(row.label(), row.count()));

        List<SectorGroup> groups = new ArrayList<>();
        taxonomy.groups().forEach((groupName, industries) -> {
            List<FacetCount> counted = industries.stream()
                    .map(industry -> new FacetCount(industry, industry,
                            countByIndustry.getOrDefault(industry, 0L)))
                    .sorted(Comparator.comparingLong(FacetCount::count).reversed()
                            .thenComparing(FacetCount::label))
                    .toList();
            groups.add(new SectorGroup(groupName, counted));
        });
        return groups;
    }

    /** One query per segment rather than a GROUP BY, because segments overlap. */
    public List<FacetCount> marketSegmentFacets() {
        List<FacetCount> facets = new ArrayList<>();
        marketSegments.segments().forEach((segment, keywords) -> {
            Map<String, Object> params = new LinkedHashMap<>();
            String sql = """
                    SELECT count(*)
                    FROM app_lm_apollo_companies
                    WHERE keywords && %s
                    """.formatted(arrayLiteral(keywords, "segKw", params));
            long count = jdbc.sql(sql).params(params).query(Long.class).single();
            facets.add(new FacetCount(segment, segment, count));
        });
        return facets;
    }

    /**
     * Reads V33's materialised {@code app_lm_apollo_keywords}, fresh only when the pipeline refreshes
     * it; {@code LIKE} suffices because every keyword there is lower-case.
     */
    public List<FacetCount> keywordSuggestions(String query, int limit, int minCompanies) {
        String pattern = escapeLikePattern(query.toLowerCase(Locale.ROOT));
        return jdbc.sql("""
                        SELECT keyword AS label, company_count AS count
                        FROM app_lm_apollo_keywords
                        WHERE keyword LIKE :contains ESCAPE '\\'
                          AND company_count >= :minCompanies
                        ORDER BY (keyword LIKE :prefix ESCAPE '\\') DESC, company_count DESC, 1
                        LIMIT :limit
                        """)
                .param("contains", "%" + pattern + "%")
                .param("prefix", pattern + "%")
                .param("minCompanies", minCompanies)
                .param("limit", limit)
                .query(ScopeBreakdown.class)
                .list()
                .stream()
                .map(row -> new FacetCount(row.label(), row.label(), row.count()))
                .toList();
    }

    /** Built from the enum's own bounds, so chip counts and the filter cannot disagree; zeroes included. */
    public List<FacetCount> employeeBandFacets() {
        return bandFacets(EmployeeBand.values(), "num_employees", "emp");
    }

    public List<FacetCount> revenueBandFacets() {
        return bandFacets(RevenueBand.values(), "annual_revenue", "rev");
    }

    private List<FacetCount> bandFacets(CompanySizeBand[] bands, String column, String prefix) {
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Long> counts = bandCounts(bandCase(bands, column, prefix, params), params);
        return Arrays.stream(bands)
                .map(band -> new FacetCount(band.value(), band.label(), counts.getOrDefault(band.value(), 0L)))
                .toList();
    }

    public List<ScopeBreakdown> countByCountry(CompanyScope scope, int limit) {
        return breakdown(scope, "company_country",
                "company_country IS NOT NULL AND company_country <> ''", limit);
    }

    private List<ScopeBreakdown> breakdown(CompanyScope scope, String column, String presenceCondition,
                                           int limit) {
        WhereClause where = buildWhere(scope);
        Map<String, Object> params = new LinkedHashMap<>(where.params());
        params.put("groupLimit", limit);
        String sql = """
                SELECT %s AS label, count(*) AS count
                FROM app_lm_apollo_companies a
                WHERE %s AND %s
                GROUP BY 1
                ORDER BY count(*) DESC, 1
                LIMIT :groupLimit
                """.formatted(column, where.sql(), presenceCondition);
        return jdbc.sql(sql).params(params).query(ScopeBreakdown.class).list();
    }

    /** An untouched filter renders as {@code TRUE}: the whole universe. */
    private WhereClause buildWhere(CompanyScope scope) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> clauses = new ArrayList<>();

        if (!scope.industries().isEmpty()) {
            clauses.add("lower(industry) IN (:industries)");
            params.put("industries", lowered(scope.industries()));
        }
        if (!scope.keywords().isEmpty()) {
            clauses.add("keywords && " + arrayLiteral(lowered(scope.keywords()), "kw", params));
        }
        List<String> segmentKeywords = marketSegments.keywordsOfAll(scope.marketSegments());
        if (!segmentKeywords.isEmpty()) {
            clauses.add("keywords && " + arrayLiteral(segmentKeywords, "segKw", params));
        }
        if (!scope.countries().isEmpty()) {
            clauses.add("company_country IN (:countries)");
            params.put("countries", scope.countries());
        }
        // The range replaces the bands rather than intersecting them.
        String employeeClause = scope.employeeRange() != null
                ? rangeClause("num_employees", scope.employeeRange(), "empRange", params)
                : bandClause(scope.employeeBands(), EmployeeBand.class, "num_employees", "emp", params);
        if (employeeClause != null) {
            clauses.add(employeeClause);
        }
        String revenueClause = scope.revenueRange() != null
                ? rangeClause("annual_revenue", scope.revenueRange(), "revRange", params)
                : bandClause(scope.revenueBands(), RevenueBand.class, "annual_revenue", "rev", params);
        if (revenueClause != null) {
            clauses.add(revenueClause);
        }
        if (!scope.offLimitsAccountIds().isEmpty()) {
            clauses.add("apollo_account_id NOT IN (:offLimitsIds)");
            params.put("offLimitsIds", scope.offLimitsAccountIds());
        }
        if (scope.triagedExclusion().isPresent()) {
            clauses.add(scope.triagedExclusion().sql());
            params.putAll(scope.triagedExclusion().params());
        }
        if (scope.nameQuery() != null) {
            clauses.add("company_name ILIKE :nameQuery ESCAPE '\\'");
            params.put("nameQuery", "%" + escapeLikePattern(scope.nameQuery()) + "%");
        }
        return new WhereClause(clauses.isEmpty() ? "TRUE" : String.join(" AND ", clauses), params);
    }

    /** Either end may be absent; {@code StrategyFilter} normalises away a range with neither. */
    private static String rangeClause(String column, NumericRange range, String prefix,
                                      Map<String, Object> params) {
        List<String> bounds = new ArrayList<>(2);
        if (range.min() != null) {
            params.put(prefix + "Min", range.min());
            bounds.add("%s >= :%sMin".formatted(column, prefix));
        }
        if (range.max() != null) {
            params.put(prefix + "Max", range.max());
            bounds.add("%s <= :%sMax".formatted(column, prefix));
        }
        return bounds.isEmpty() ? null : "(" + String.join(" AND ", bounds) + ")";
    }

    /** An OR of closed ranges; Unknown joins as a null test, the only way to reach rows with no figure. */
    private static <B extends Enum<B> & CompanySizeBand> String bandClause(
            List<String> bandValues, Class<B> type, String column, String prefix, Map<String, Object> params) {
        List<String> ranges = new ArrayList<>();
        int index = 0;
        for (String bandValue : bandValues) {
            B band = ApiValueEnum.fromValue(type, bandValue);
            if (band == null) {
                continue;
            }
            if (band.isUnknown()) {
                ranges.add(column + " IS NULL");
                continue;
            }
            ranges.add(boundsClause(column, band.lowerBound(), band.upperBound(), prefix, index++, params));
        }
        return ranges.isEmpty() ? null : "(" + String.join(" OR ", ranges) + ")";
    }

    private static String boundsClause(String column, long lowerBound, Long upperBound, String prefix,
                                       int index, Map<String, Object> params) {
        String lowParam = prefix + "Low" + index;
        params.put(lowParam, lowerBound);
        if (upperBound == null) {
            return "%s >= :%s".formatted(column, lowParam);
        }
        String highParam = prefix + "High" + index;
        params.put(highParam, upperBound);
        return "%s BETWEEN :%s AND :%s".formatted(column, lowParam, highParam);
    }

    /** The Unknown band's null test goes first so it wins before any range is considered. */
    private static String bandCase(CompanySizeBand[] bands, String column, String prefix,
                                   Map<String, Object> params) {
        StringBuilder expression = new StringBuilder("CASE");
        for (CompanySizeBand band : bands) {
            if (band.isUnknown()) {
                expression.append(" WHEN ").append(column).append(" IS NULL THEN :").append(prefix).append("Unknown");
                params.put(prefix + "Unknown", band.value());
            }
        }
        int index = 0;
        for (CompanySizeBand band : bands) {
            if (band.isUnknown()) {
                continue;
            }
            expression.append(" WHEN ")
                    .append(boundsClause(column, band.lowerBound(), band.upperBound(), prefix + "Case", index, params))
                    .append(" THEN :").append(prefix).append("Label").append(index);
            params.put(prefix + "Label" + index, band.value());
            index++;
        }
        return expression.append(" END").toString();
    }

    private Map<String, Long> bandCounts(String bandExpression, Map<String, Object> params) {
        Map<String, Long> counts = new LinkedHashMap<>();
        String sql = """
                SELECT %s AS label, count(*) AS count
                FROM app_lm_apollo_companies
                GROUP BY 1
                """.formatted(bandExpression);
        jdbc.sql(sql).params(params).query(ScopeBreakdown.class).list()
                .forEach(row -> counts.put(row.label(), row.count()));
        return counts;
    }

    /**
     * Hand-written so nullable numerics come back boxed. {@code founded_year} goes through
     * {@link Number}: the driver returns a {@code smallint} as {@code Integer}, and a {@code (Short)}
     * cast threw ClassCastException — a 500 — on the first row carrying a year.
     */
    private static final RowMapper<CompanyRow> COMPANY_ROW_MAPPER = ApolloCompanyQueryService::mapRow;

    private static CompanyRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new CompanyRow(
                rs.getString("apollo_account_id"),
                rs.getString("company_name"),
                rs.getString("industry"),
                rs.getString("company_country"),
                rs.getString("company_city"),
                (Integer) rs.getObject("num_employees"),
                (Long) rs.getObject("annual_revenue"),
                rs.getString("website"),
                rs.getString("logo_url"),
                rs.getString("short_description"),
                intOrNull((Number) rs.getObject("founded_year")),
                rs.getString("company_linkedin_url"),
                rs.getString("facebook_url"),
                rs.getString("twitter_url"),
                rs.getString("company_phone"),
                rs.getString("company_state"),
                rs.getString("company_address"),
                rs.getString("parent_company"),
                (Long) rs.getObject("total_funding"),
                rs.getString("latest_funding"),
                (Long) rs.getObject("latest_funding_amount"),
                rs.getObject("last_raised_at", LocalDate.class),
                (Integer) rs.getObject("number_of_retail_locations"),
                stringList(rs, "keywords"),
                stringList(rs, "technologies"),
                stringList(rs, "sic_codes"),
                stringList(rs, "naics_codes"));
    }

    /** An absent {@code text[]} arrives as a null {@link Array}, not as an empty one. */
    private static List<String> stringList(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        String[] values = (String[]) array.getArray();
        return values == null ? List.of() : Arrays.stream(values).filter(Objects::nonNull).toList();
    }

    /**
     * {@code ARRAY[:p0, :p1]::text[]}, one bound parameter per element. A single {@code String[]}
     * parameter is expanded by Spring into {@code ?, ?, ?}, and without the {@code ::text[]} cast the
     * driver's {@code varchar} binding makes Postgres refuse {@code &&} — a 500.
     */
    private static String arrayLiteral(List<String> values, String prefix, Map<String, Object> params) {
        List<String> placeholders = new ArrayList<>(values.size());
        int index = 0;
        for (String value : values) {
            String name = prefix + index++;
            params.put(name, value);
            placeholders.add(":" + name);
        }
        return "ARRAY[" + String.join(", ", placeholders) + "]::text[]";
    }

    private static Integer intOrNull(Number value) {
        return value == null ? null : value.intValue();
    }

    private static List<String> lowered(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }

    private static String escapeLikePattern(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
