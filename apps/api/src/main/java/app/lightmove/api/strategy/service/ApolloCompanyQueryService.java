package app.lightmove.api.strategy.service;

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
 * Every read of the company universe — {@code app_lm_apollo_companies}, 71,822 GCC companies loaded
 * by the pipeline and read-only to this application.
 *
 * <p>{@code JdbcClient} rather than JPA: every read here is an aggregate or a filtered projection
 * over ETL-owned reference data, and an entity would buy a lifecycle for rows nothing may write.
 *
 * <p>Two facts about Apollo shape everything below. <b>Size arrives raw</b> — {@code num_employees}
 * and {@code annual_revenue} are figures, not pre-bucketed strings, so a band selection becomes an OR
 * of numeric ranges built from {@link EmployeeBand} / {@link RevenueBand}. And <b>revenue is
 * sparse</b>: 7,132 rows in 71,822 carry a figure, which is why {@link RevenueBand#R_UNKNOWN} is a
 * selectable band rendering as {@code annual_revenue IS NULL}.
 *
 * <p>Facet counts are taken over the whole universe, not the current selection, so the five
 * accordions are one cacheable read that no filter invalidates.
 */
@Service
@RequiredArgsConstructor
public class ApolloCompanyQueryService {

    /**
     * Every column the list and the write-path snapshots need, in one place so they cannot drift.
     * The universe's other columns hold Apollo's own CRM state and the loader's bookkeeping.
     */
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

    /** How many companies the scope matches. An empty scope is the whole universe, not nothing. */
    public long count(CompanyScope scope) {
        WhereClause where = buildWhere(scope);
        return bind(jdbc.sql("SELECT count(*) FROM app_lm_apollo_companies WHERE " + where.sql()),
                where.params()).query(Long.class).single();
    }

    /**
     * One page of the scope, sorted by a column from {@link CompanySortField}'s allowlist. The caller
     * supplies the page, the size and the sort; the scope itself is resolved server-side from the
     * mandate's saved filter and never from a request parameter.
     */
    public List<CompanyRow> search(CompanyScope scope, CompanySortField sort, SortDirection direction,
                                   int page, int size) {
        WhereClause where = buildWhere(scope);
        Map<String, Object> params = new LinkedHashMap<>(where.params());
        String sql = """
                SELECT %s
                FROM app_lm_apollo_companies
                WHERE %s
                ORDER BY %s, apollo_account_id
                LIMIT :size OFFSET :offset
                """.formatted(ROW_COLUMNS, where.sql(), sort.orderByTerms(direction));
        params.put("size", size);
        params.put("offset", (long) page * size);
        return bind(jdbc.sql(sql), params).query(COMPANY_ROW_MAPPER).list();
    }

    /**
     * The named companies, whatever the scope — the write path's seam. Every caller that snapshots a
     * company resolves it here, so only a company the universe holds can be stored.
     */
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

    /**
     * Name-prefix search for the company pickers. Ranked so a prefix match beats one buried mid-name,
     * then by size: the company meant by three letters is almost always the biggest starting with them.
     */
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
     * The one company a research answer names, or nothing. The LinkedIn slug is the strong key —
     * two firms cannot share one — and an exact name is trusted only when it is unique, because
     * silently mapping the wrong "Alpha" is worse than mapping none. Nothing here is ever fuzzy.
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
     * The Industry accordion, arranged into the taxonomy's groups. Groups keep the file's order so
     * the sidebar does not rearrange itself when the pipeline reloads; industries inside a group are
     * ranked most populous first.
     *
     * <p>An industry the taxonomy does not cover is dropped here and would vanish from the sidebar.
     * {@code SectorTaxonomyCoverageIntegrationTest} asserts that set is empty against the real table.
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

    /**
     * The Market Segments accordion: how many companies each segment's keywords reach.
     *
     * <p>One query per segment rather than one GROUP BY, because segments <b>overlap</b> — a company
     * can be B2B and SaaS at once, and a grouped count would have to pick one. The counts therefore
     * add up to more than the universe. Segments keep the file's order, not size order.
     */
    public List<FacetCount> marketSegmentFacets() {
        List<FacetCount> facets = new ArrayList<>();
        marketSegments.segments().forEach((segment, keywords) -> {
            Map<String, Object> params = new LinkedHashMap<>();
            String sql = """
                    SELECT count(*)
                    FROM app_lm_apollo_companies
                    WHERE keywords && %s
                    """.formatted(arrayLiteral(keywords, "segKw", params));
            long count = bind(jdbc.sql(sql), params).query(Long.class).single();
            facets.add(new FacetCount(segment, segment, count));
        });
        return facets;
    }

    /**
     * The Company Keywords box. Ranked like {@link #typeahead}: a prefix match beats one buried
     * mid-word, then the biggest slice of the market first.
     *
     * <p>Reads {@code app_lm_apollo_keywords}, which V33 materialises; it follows the universe only
     * when the pipeline refreshes it. {@code LIKE} rather than {@code ILIKE} because every keyword in
     * that table is already lower-case.
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

    /**
     * The two size accordions. One GROUP BY over a CASE built from the enum's own bounds, so the chip
     * counts and the filter behind the chip cannot disagree. Bands come back in enum order including
     * any counting zero: a missing band reads as "no such size", a zero as "none in this market".
     */
    public List<FacetCount> employeeBandFacets() {
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Long> counts = bandCounts(employeeBandCase(params), params);
        return Arrays.stream(EmployeeBand.values())
                .map(band -> new FacetCount(band.value(), band.label(),
                        counts.getOrDefault(band.value(), 0L)))
                .toList();
    }

    /** The Revenue accordion, Unknown included — see {@link RevenueBand#R_UNKNOWN}. */
    public List<FacetCount> revenueBandFacets() {
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Long> counts = bandCounts(revenueBandCase(params), params);
        return Arrays.stream(RevenueBand.values())
                .map(band -> new FacetCount(band.value(), band.label(),
                        counts.getOrDefault(band.value(), 0L)))
                .toList();
    }

    /** The scope's most populous industries, largest first — a report aggregate. */
    public List<ScopeBreakdown> countBySector(CompanyScope scope, int limit) {
        return breakdown(scope, "industry", "industry IS NOT NULL AND industry <> ''", limit);
    }

    /** The scope's most populous countries, largest first — a report aggregate. */
    public List<ScopeBreakdown> countByCountry(CompanyScope scope, int limit) {
        return breakdown(scope, "company_country",
                "company_country IS NOT NULL AND company_country <> ''", limit);
    }

    /** The scope's most populous cities, largest first — a report aggregate. */
    public List<ScopeBreakdown> countByCity(CompanyScope scope, int limit) {
        return breakdown(scope, "company_city",
                "company_city IS NOT NULL AND company_city <> ''", limit);
    }

    /**
     * The shared shape behind every grouped aggregate. {@code presenceCondition} drops rows the
     * grouping column is missing on, since a bar labelled with a blank is noise.
     */
    private List<ScopeBreakdown> breakdown(CompanyScope scope, String column, String presenceCondition,
                                           int limit) {
        WhereClause where = buildWhere(scope);
        Map<String, Object> params = new LinkedHashMap<>(where.params());
        params.put("groupLimit", limit);
        String sql = """
                SELECT %s AS label, count(*) AS count
                FROM app_lm_apollo_companies
                WHERE %s AND %s
                GROUP BY 1
                ORDER BY count(*) DESC, 1
                LIMIT :groupLimit
                """.formatted(column, where.sql(), presenceCondition);
        return bind(jdbc.sql(sql), params).query(ScopeBreakdown.class).list();
    }

    /**
     * Every criterion the sidebar can set, ANDed. Each is omitted when it selects nothing, so an
     * untouched filter renders as {@code TRUE} and returns the whole universe.
     */
    private WhereClause buildWhere(CompanyScope scope) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> clauses = new ArrayList<>();

        if (!scope.industries().isEmpty()) {
            // Lower-cased on both sides: a saved filter should not depend on Apollo's vocabulary
            // staying lower-case.
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
        // The range wins outright rather than intersecting: a consultant who typed 250-400 means
        // that, not "and also 201-500".
        String employeeClause = scope.employeeRange() != null
                ? rangeClause("num_employees", scope.employeeRange(), "empRange", params)
                : employeeBandClause(scope.employeeBands(), params);
        if (employeeClause != null) {
            clauses.add(employeeClause);
        }
        String revenueClause = scope.revenueRange() != null
                ? rangeClause("annual_revenue", scope.revenueRange(), "revRange", params)
                : revenueBandClause(scope.revenueBands(), params);
        if (revenueClause != null) {
            clauses.add(revenueClause);
        }
        if (!scope.offLimitsAccountIds().isEmpty()) {
            clauses.add("apollo_account_id NOT IN (:offLimitsIds)");
            params.put("offLimitsIds", scope.offLimitsAccountIds());
        }
        if (scope.nameQuery() != null) {
            clauses.add("company_name ILIKE :nameQuery ESCAPE '\\'");
            params.put("nameQuery", "%" + escapeLikePattern(scope.nameQuery()) + "%");
        }
        return new WhereClause(clauses.isEmpty() ? "TRUE" : String.join(" AND ", clauses), params);
    }

    /**
     * A typed custom range over one column. Either end may be absent; a range with neither never
     * reaches here, because {@code StrategyFilter} normalises it away.
     */
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

    /** Selected headcount bands as an OR of closed numeric ranges. */
    private static String employeeBandClause(List<String> bandValues, Map<String, Object> params) {
        List<String> ranges = new ArrayList<>();
        int index = 0;
        for (String bandValue : bandValues) {
            EmployeeBand band = EmployeeBand.fromValue(bandValue);
            if (band == null) {
                continue;
            }
            ranges.add(boundsClause("num_employees", band.lowerBound(), band.upperBound(),
                    "emp", index++, params));
        }
        return ranges.isEmpty() ? null : "(" + String.join(" OR ", ranges) + ")";
    }

    /**
     * Selected revenue bands as an OR of numeric ranges, with Unknown joining as a null test. A row
     * with no figure falls in no numeric band, so Unknown is how those 64,690 companies are reached.
     */
    private static String revenueBandClause(List<String> bandValues, Map<String, Object> params) {
        List<String> ranges = new ArrayList<>();
        int index = 0;
        for (String bandValue : bandValues) {
            RevenueBand band = RevenueBand.fromValue(bandValue);
            if (band == null) {
                continue;
            }
            if (band.isUnknown()) {
                ranges.add("annual_revenue IS NULL");
                continue;
            }
            ranges.add(boundsClause("annual_revenue", band.lowerBound(), band.upperBound(),
                    "rev", index++, params));
        }
        return ranges.isEmpty() ? null : "(" + String.join(" OR ", ranges) + ")";
    }

    /** One band's range over one column: BETWEEN when bounded, {@code >=} for an open-ended top band. */
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

    /** A CASE mapping each headcount to its band's wire token, built from the enum's own bounds. */
    private static String employeeBandCase(Map<String, Object> params) {
        StringBuilder expression = new StringBuilder("CASE");
        int index = 0;
        for (EmployeeBand band : EmployeeBand.values()) {
            expression.append(" WHEN ")
                    .append(boundsClause("num_employees", band.lowerBound(), band.upperBound(),
                            "empCase", index, params))
                    .append(" THEN :empLabel").append(index);
            params.put("empLabel" + index, band.value());
            index++;
        }
        return expression.append(" END").toString();
    }

    /** The same for revenue, with the null case first so it wins before any range is considered. */
    private static String revenueBandCase(Map<String, Object> params) {
        StringBuilder expression = new StringBuilder("CASE WHEN annual_revenue IS NULL THEN :revUnknown");
        params.put("revUnknown", RevenueBand.R_UNKNOWN.value());
        int index = 0;
        for (RevenueBand band : RevenueBand.values()) {
            if (band.isUnknown()) {
                continue;
            }
            expression.append(" WHEN ")
                    .append(boundsClause("annual_revenue", band.lowerBound(), band.upperBound(),
                            "revCase", index, params))
                    .append(" THEN :revLabel").append(index);
            params.put("revLabel" + index, band.value());
            index++;
        }
        return expression.append(" END").toString();
    }

    /** Counts per band token for a CASE expression, over the whole universe. */
    private Map<String, Long> bandCounts(String bandExpression, Map<String, Object> params) {
        Map<String, Long> counts = new LinkedHashMap<>();
        String sql = """
                SELECT %s AS label, count(*) AS count
                FROM app_lm_apollo_companies
                GROUP BY 1
                """.formatted(bandExpression);
        bind(jdbc.sql(sql), params).query(ScopeBreakdown.class).list()
                .forEach(row -> counts.put(row.label(), row.count()));
        return counts;
    }

    /**
     * Hand-written rather than the reflective mapper: the nullable numerics have to come back as boxed
     * types so a missing revenue reads as absent rather than as zero.
     *
     * <p>{@code founded_year} goes through {@link Number} rather than a direct cast. It is a
     * {@code smallint}, and {@code getObject} on one returns an {@code Integer} from this driver —
     * a {@code (Short)} cast compiles, reads correctly, and then throws ClassCastException on the
     * first row that actually carries a year, which is a 500 on the list rather than a wrong value.
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
     * A Postgres array built from one bound parameter per element — {@code ARRAY[:p0, :p1]}.
     *
     * <p>Not a single {@code String[]} parameter, which is the obvious way and is wrong here:
     * Spring's named-parameter expansion turns an array into a comma-separated list of placeholders,
     * rendering this as {@code keywords && ?, ?, ?}. Building the literal keeps every value bound
     * while still producing an array {@code &&} can use against {@code idx_lm_apollo_kw}.
     *
     * <p>The {@code ::text[]} cast is not decoration. The driver binds a String as {@code varchar}, so
     * the literal comes out as {@code character varying[]} and Postgres refuses
     * {@code text[] && character varying[]} — "operator does not exist", a 500.
     *
     * <p>Array overlap rather than unnest-and-lower: every keyword in the table is already lower-case,
     * so this form can use the GIN index where the safer-looking one cannot.
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

    /** Widen whatever numeric type the driver chose, or keep the absence. */
    private static Integer intOrNull(Number value) {
        return value == null ? null : value.intValue();
    }

    private static List<String> lowered(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }

    /** Backslash-escape LIKE's wildcards so the user's text matches literally. */
    private static String escapeLikePattern(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Map<String, Object> params) {
        JdbcClient.StatementSpec bound = spec;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            bound = bound.param(entry.getKey(), entry.getValue());
        }
        return bound;
    }
}
